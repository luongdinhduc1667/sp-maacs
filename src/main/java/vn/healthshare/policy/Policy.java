package vn.healthshare.policy;

import java.math.BigInteger;
import java.util.*;

/** Monotone Boolean policy compiler, bounded DNF expansion and an AND LSSS per clause. */
public final class Policy {
    public static final int MAX_CLAUSES = 32, MAX_ROWS = 64, MAX_TOTAL_ROWS = 256;
    public record Label(String authority, String name) {
        public Label {
            if (authority == null || name == null || !authority.matches("[\\p{L}\\p{N}_-]+") || !name.matches("[\\p{L}\\p{N}_-]+"))
                throw new IllegalArgumentException("Tên thuộc tính phải có dạng AA.Name.");
        }
        @Override public String toString() { return authority + "." + name; }
    }
    public record Attribute(Label label, String value) {
        public Attribute { Objects.requireNonNull(label); if (value == null || value.isBlank() || value.length() > 160) throw new IllegalArgumentException("Giá trị thuộc tính rỗng hoặc quá dài."); }
        public static Attribute of(String authority, String name, String value) { return new Attribute(new Label(authority, name), value); }
        @Override public String toString() { return label+"="+quote(value); }
    }
    private final List<List<Attribute>> clauses;
    private Policy(List<List<Attribute>> clauses) { this.clauses = clauses.stream().map(List::copyOf).toList(); }
    public List<List<Attribute>> clauses() { return clauses; }
    public int rowCount() { return clauses.stream().mapToInt(List::size).sum(); }
    public String hidden() { return clauses.stream().map(c -> "("+String.join(" AND ", c.stream().map(a -> a.label()+"=*").toList())+")").reduce((a,b)->a+" OR "+b).orElseThrow(); }
    public String canonical() { return clauses.stream().map(c -> "("+String.join(" AND ", c.stream().map(Attribute::toString).toList())+")").reduce((a,b)->a+" OR "+b).orElseThrow(); }
    public static Policy parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Hãy nhập chính sách truy cập.");
        if (text.length() > 16000) throw new IllegalArgumentException("Chính sách quá dài (tối đa 16000 ký tự).");
        Parser p = new Parser(text);
        List<List<Attribute>> result = p.or();
        if (p.at != p.tokens.size()) throw p.error("Ký hiệu không hợp lệ: "+p.peek());
        List<List<Attribute>> unique = new ArrayList<>();
        for (List<Attribute> clause : result) {
            List<Attribute> row = List.copyOf(new LinkedHashSet<>(clause));
            if (unique.stream().noneMatch(c -> new HashSet<>(c).equals(new HashSet<>(row)))) unique.add(row);
        }
        // Absorption A OR (A AND B) = A. Preserve the remaining author-defined order.
        List<List<Attribute>> simplified = unique.stream().filter(c -> unique.stream().noneMatch(other -> other.size()<c.size() && c.containsAll(other))).toList();
        validate(simplified);
        return new Policy(simplified);
    }
    private static void validate(List<List<Attribute>> clauses) {
        if (clauses.size()>MAX_CLAUSES || clauses.stream().anyMatch(c->c.size()>MAX_ROWS) || clauses.stream().mapToInt(List::size).sum()>MAX_TOTAL_ROWS)
            throw new IllegalArgumentException("DNF quá lớn: tối đa 32 nhánh, 64 dòng/nhánh và 256 dòng tổng cộng.");
    }
    public static String quote(String value) {
        return value.matches("[\\p{L}\\p{N}_:/.-]+") && !value.equalsIgnoreCase("AND") && !value.equalsIgnoreCase("OR") ? value : "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";
    }

    /** Telescoping AND matrix: sum of all rows is (1,0,...,0), hence every mu_x = 1. */
    public static List<List<BigInteger>> andMatrix(int rows) {
        if (rows < 1 || rows > MAX_ROWS) throw new IllegalArgumentException("Số dòng LSSS không hợp lệ.");
        List<List<BigInteger>> matrix = new ArrayList<>();
        for (int i=0; i<rows; i++) {
            List<BigInteger> row = new ArrayList<>(Collections.nCopies(rows, BigInteger.ZERO));
            row.set(i, i==0 ? BigInteger.ONE : BigInteger.ONE.negate());
            if (i+1<rows) row.set(i+1, BigInteger.ONE);
            matrix.add(List.copyOf(row));
        }
        return List.copyOf(matrix);
    }
    public static BigInteger dot(List<BigInteger> a, List<BigInteger> b, BigInteger modulus) {
        if (a.size()!=b.size()) throw new IllegalArgumentException("Sai kích thước vector.");
        BigInteger sum=BigInteger.ZERO;
        for(int i=0;i<a.size();i++) sum=sum.add(a.get(i).multiply(b.get(i)));
        return sum.mod(modulus);
    }
    private record Token(String text, boolean quoted, int offset) {}
    private static final class Parser {
        final List<Token> tokens = new ArrayList<>(); int at, depth;
        Parser(String input) {
            for(int i=0;i<input.length();) {
                char ch=input.charAt(i);
                if(Character.isWhitespace(ch)) {i++;continue;}
                int start=i;
                if(ch=='\'' || ch=='"') {
                    char quote=ch; i++; StringBuilder value=new StringBuilder(); boolean closed=false;
                    while(i<input.length()) {
                        ch=input.charAt(i++);
                        if(ch==quote) {closed=true;break;}
                        if(ch=='\\') {
                            if(i>=input.length()) throw new IllegalArgumentException("Thiếu ký tự sau dấu \\.");
                            ch=input.charAt(i++);
                            if(ch!='\\' && ch!='\'' && ch!='"') throw new IllegalArgumentException("Chỉ hỗ trợ escape dấu nháy và dấu \\.");
                        }
                        value.append(ch);
                    }
                    if(!closed) throw new IllegalArgumentException("Thiếu dấu đóng chuỗi tại vị trí "+(start+1));
                    tokens.add(new Token(value.toString(),true,start));
                } else if("()=&|".indexOf(ch)>=0) {
                    i++;
                    if((ch=='&'||ch=='|') && i<input.length() && input.charAt(i)==ch) i++;
                    tokens.add(new Token(input.substring(start,i),false,start));
                } else {
                    while(i<input.length() && !Character.isWhitespace(input.charAt(i)) && "()=&|\"'".indexOf(input.charAt(i))<0) i++;
                    tokens.add(new Token(input.substring(start,i),false,start));
                }
            }
        }
        String peek() { return at<tokens.size()?tokens.get(at).text():"<kết thúc>"; }
        boolean take(String... choices) {
            if(at==tokens.size()||tokens.get(at).quoted()) return false;
            for(String s:choices) if(peek().equalsIgnoreCase(s)) {at++;return true;}
            return false;
        }
        IllegalArgumentException error(String message) { return new IllegalArgumentException(message+" (vị trí "+(at<tokens.size()?tokens.get(at).offset()+1:"cuối")+")."); }
        List<List<Attribute>> or() {
            List<List<Attribute>> out=and();
            while(take("OR","|","||")) { out=new ArrayList<>(out); out.addAll(and()); validate(out); }
            return out;
        }
        List<List<Attribute>> and() {
            List<List<Attribute>> out=atom();
            while(take("AND","&","&&")) {
                List<List<Attribute>> right=atom(), next=new ArrayList<>();
                for(List<Attribute> l:out) for(List<Attribute> r:right) {
                    List<Attribute> c=new ArrayList<>(l); c.addAll(r); next.add(c); validate(next);
                }
                out=next;
            }
            return out;
        }
        List<List<Attribute>> atom() {
            if(take("(")) {
                if(++depth>64) throw error("Ngoặc lồng quá sâu");
                List<List<Attribute>> out=or();
                if(!take(")")) throw error("Thiếu dấu )"); depth--; return out;
            }
            if(at>=tokens.size() || tokens.get(at).quoted()) throw error("Cần tên AA.Name");
            String[] label=tokens.get(at++).text().split("\\.",-1);
            if(label.length!=2) throw error("Tên thuộc tính phải có dạng AA.Name");
            if(!take("=")) throw error("Thiếu dấu =");
            if(at>=tokens.size() || (!tokens.get(at).quoted() && Set.of("AND","OR","&","&&","|","||","(",")","=").contains(peek().toUpperCase(Locale.ROOT)))) throw error("Thiếu giá trị thuộc tính");
            return List.of(List.of(new Attribute(new Label(label[0],label[1]),tokens.get(at++).text())));
        }
    }
}
