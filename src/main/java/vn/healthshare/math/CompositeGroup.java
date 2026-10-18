package vn.healthshare.math;

import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.jpbc.PairingParameters;
import it.unisa.dia.gas.plaf.jpbc.pairing.a1.TypeA1CurveGenerator;
import it.unisa.dia.gas.plaf.jpbc.pairing.a1.TypeA1Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.parameters.PropertiesParameters;
import it.unisa.dia.gas.plaf.jpbc.util.ElementUtils;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Arrays;

/** Real JPBC 2.0.0 symmetric Type A1 elliptic-curve pairing, N = p1*p2*p3. */
public final class CompositeGroup {
    public static final int PAPER_PRIME_BITS = 517;
    public enum Kind { G, GT }
    private final Pairing pairing;
    private final PairingParameters parameters;
    private final BigInteger[] primes;
    private final BigInteger order;
    private final SecureRandom random=new SecureRandom();
    private final Element g, gt, i2, i3;
    private long pairings, powers, products;
    public CompositeGroup(int primeBits) {
        checkCancelled();
        if(primeBits<64 || primeBits>1024) throw new IllegalArgumentException("Số bit phải từ 64 đến 1024.");
        parameters=loadParameters(primeBits);
        primes=new BigInteger[]{parameters.getBigInteger("n0"),parameters.getBigInteger("n1"),parameters.getBigInteger("n2")};
        for(BigInteger p:primes) if(p.bitLength()!=primeBits || !p.isProbablePrime(80)) throw new IllegalStateException("Tham số Type A1 không đúng kích thước/số nguyên tố.");
        if(primes[0].equals(primes[1])||primes[0].equals(primes[2])||primes[1].equals(primes[2]))throw new IllegalStateException("Ba số nguyên tố phải khác nhau.");
        order=primes[0].multiply(primes[1]).multiply(primes[2]);
        if(!order.equals(parameters.getBigInteger("n")))throw new IllegalStateException("Sai bậc nhóm Type A1.");
        checkCancelled();pairing=new TypeA1Pairing(random,parameters);
        it.unisa.dia.gas.jpbc.Element full=pairing.getG1().newRandomElement();
        g=wrap(Kind.G,ElementUtils.getGenerator(pairing,full.duplicate(),parameters,0,3));
        i2=wrap(Kind.G,ElementUtils.getGenerator(pairing,full.duplicate(),parameters,1,3));
        i3=wrap(Kind.G,ElementUtils.getGenerator(pairing,full.duplicate(),parameters,2,3));
        gt=wrap(Kind.GT,pairing.pairing(g.value,g.value));
        checkCancelled();if(g.value.isOne()||i2.value.isOne()||i3.value.isOne()||gt.value.isOne())throw new IllegalStateException("Generator suy biến; hãy tạo lại phiên.");
    }
    private static synchronized PairingParameters loadParameters(int bits) {
        Path file=Path.of("build/private/type-a1-3x"+bits+".properties");
        try {
            if(Files.exists(file))try(InputStream in=Files.newInputStream(file)){return new PropertiesParameters().load(in);}
            checkCancelled();PairingParameters params=new TypeA1CurveGenerator(3,bits).generate();checkCancelled();
            Files.createDirectories(file.getParent());Files.writeString(file,params.toString());return params;
        } catch(IOException e){throw new UncheckedIOException("Không thể lưu/đọc tham số Type A1 riêng của máy.",e);}
    }
    /** Public curve parameters intentionally omit n0,n1,n2 (the secret factorization of N). */
    public PairingParameters publicPairingParameters(){PropertiesParameters out=new PropertiesParameters();for(String k:new String[]{"type","p","n","l"})out.put(k,parameters.getString(k));return out;}
    public BigInteger order(){return order;}
    public int primeBits(){return primes[0].bitLength();}
    public int[] primeBitLengths(){return Arrays.stream(primes).mapToInt(BigInteger::bitLength).toArray();}
    public static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Đã yêu cầu hủy phép tính JPBC.");}
    public BigInteger scalar(){checkCancelled();return nonzeroBelow(order);}
    public BigInteger p1Scalar(){return nonzeroBelow(primes[0]);}
    private BigInteger nonzeroBelow(BigInteger limit){BigInteger x;do{x=new BigInteger(limit.bitLength(),random);}while(x.signum()==0||x.compareTo(limit)>=0);return x;}
    public Element generator(){return g;}
    public Element targetGenerator(){return gt;}
    public Element subgroup3(){return i3.pow(nonzeroBelow(primes[2]));}
    public Element subgroup2(){return i2.pow(nonzeroBelow(primes[1]));}
    public Element identity(Kind kind){return wrap(kind,(kind==Kind.G?pairing.getG1():pairing.getGT()).newOneElement());}
    private Element wrap(Kind kind,it.unisa.dia.gas.jpbc.Element value){return new Element(kind,value.getImmutable());}
    public Element pairing(Element a,Element b){checkCancelled();require(a,Kind.G);require(b,Kind.G);pairings++;Element result=wrap(Kind.GT,pairing.pairing(a.value,b.value));checkCancelled();return result;}
    private void require(Element element,Kind kind){if(element==null||element.owner()!=this||element.kind!=kind)throw new IllegalArgumentException("Phần tử sai nhóm hoặc sai phiên hệ thống.");}
    public Counts counts(){return new Counts(pairings,powers,products);}
    public record Counts(long pairings,long powers,long products){public Counts minus(Counts c){return new Counts(pairings-c.pairings,powers-c.powers,products-c.products);}}
    public final class Element {
        private final Kind kind;
        private final it.unisa.dia.gas.jpbc.Element value;
        private Element(Kind kind,it.unisa.dia.gas.jpbc.Element value){this.kind=kind;this.value=value;}
        private CompositeGroup owner(){return CompositeGroup.this;}
        public Kind kind(){return kind;}
        public Element multiply(Element other){require(other,kind);products++;return wrap(kind,value.duplicate().mul(other.value));}
        public Element divide(Element other){require(other,kind);products++;return wrap(kind,value.duplicate().div(other.value));}
        public Element pow(BigInteger exponent){checkCancelled();powers++;Element result=wrap(kind,value.duplicate().pow(exponent.mod(order)));checkCancelled();return result;}
        /** Real JPBC curve/extension-field encoding; elements do not store discrete logarithms. */
        public byte[] bytes(){return value.toBytes();}
        @Override public boolean equals(Object object){return object instanceof CompositeGroup.Element e&&e.owner()==owner()&&e.kind==kind&&value.isEqual(e.value);}
        @Override public int hashCode(){return 31*System.identityHashCode(owner())+31*kind.hashCode()+Arrays.hashCode(bytes());}
        @Override public String toString(){byte[] b=bytes();return kind+"["+java.util.HexFormat.of().formatHex(b,0,Math.min(9,b.length))+"…]";}
    }
}
