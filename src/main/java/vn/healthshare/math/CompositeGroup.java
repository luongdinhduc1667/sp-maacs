package vn.healthshare.math;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

/** Educational exponent model of G and GT of order N=p1*p2*p3; NOT a cryptographic pairing. */
public final class CompositeGroup {
    public enum Kind { G, GT }
    private final BigInteger[] primes;
    private final BigInteger order;
    private final SecureRandom random;
    private long pairings, powers, products;

    public CompositeGroup(int primeBits) {
        if (primeBits < 64 || primeBits > 1024) throw new IllegalArgumentException("Số bit phải từ 64 đến 1024.");
        random = new SecureRandom();
        primes = new BigInteger[3];
        for (int i = 0; i < 3; i++) {
            BigInteger p;
            do { p = BigInteger.probablePrime(primeBits, random); }
            while (Arrays.asList(primes).contains(p));
            primes[i] = p;
        }
        order = primes[0].multiply(primes[1]).multiply(primes[2]);
    }

    public BigInteger order() { return order; }
    public int primeBits() { return primes[0].bitLength(); }
    public BigInteger scalar() { return nonzeroBelow(order); }
    public BigInteger p1Scalar() { return nonzeroBelow(primes[0]); }
    private BigInteger nonzeroBelow(BigInteger limit) {
        BigInteger x;
        do { x = new BigInteger(limit.bitLength(), random); }
        while (x.signum() == 0 || x.compareTo(limit) >= 0);
        return x;
    }
    public Element generator() { return element(Kind.G, BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO); }
    public Element targetGenerator() { return element(Kind.GT, BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO); }
    public Element subgroup3() { return element(Kind.G, BigInteger.ZERO, BigInteger.ZERO, scalar()); }
    public Element subgroup2() { return element(Kind.G, BigInteger.ZERO, scalar(), BigInteger.ZERO); }
    public Element identity(Kind kind) { return element(kind, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO); }
    private Element element(Kind kind, BigInteger a, BigInteger b, BigInteger c) {
        return new Element(kind, new BigInteger[] {a.mod(primes[0]), b.mod(primes[1]), c.mod(primes[2])});
    }
    public Element pairing(Element a, Element b) {
        require(a, Kind.G); require(b, Kind.G); pairings++;
        return element(Kind.GT, a.exponents[0].multiply(b.exponents[0]),
                a.exponents[1].multiply(b.exponents[1]), a.exponents[2].multiply(b.exponents[2]));
    }
    private void require(Element a, Kind kind) {
        if (a == null || a.owner() != this || a.kind != kind) throw new IllegalArgumentException("Phần tử sai nhóm hoặc sai phiên hệ thống.");
    }
    public Counts counts() { return new Counts(pairings, powers, products); }
    public record Counts(long pairings, long powers, long products) {
        public Counts minus(Counts c) { return new Counts(pairings-c.pairings, powers-c.powers, products-c.products); }
    }

    public final class Element {
        private final Kind kind;
        private final BigInteger[] exponents;
        private Element(Kind kind, BigInteger[] exponents) { this.kind = kind; this.exponents = exponents; }
        private CompositeGroup owner() { return CompositeGroup.this; }
        public Kind kind() { return kind; }
        public Element multiply(Element other) {
            require(other, kind); products++;
            return element(kind, exponents[0].add(other.exponents[0]), exponents[1].add(other.exponents[1]), exponents[2].add(other.exponents[2]));
        }
        public Element divide(Element other) {
            require(other, kind); products++;
            return element(kind, exponents[0].subtract(other.exponents[0]), exponents[1].subtract(other.exponents[1]), exponents[2].subtract(other.exponents[2]));
        }
        public Element pow(BigInteger exponent) {
            powers++;
            return element(kind, exponents[0].multiply(exponent), exponents[1].multiply(exponent), exponents[2].multiply(exponent));
        }
        /** Fixed width, deterministic encoding; visibly contains exponents in this simulation backend. */
        public byte[] bytes() {
            int width = (Arrays.stream(primes).mapToInt(BigInteger::bitLength).max().orElseThrow()+7)/8;
            ByteBuffer out = ByteBuffer.allocate(1+3*width).put((byte) kind.ordinal());
            for (BigInteger x : exponents) {
                byte[] b = x.toByteArray();
                int length = Math.min(b.length, width);
                out.put(new byte[width-length]).put(b, b.length-length, length);
            }
            return out.array();
        }
        @Override public boolean equals(Object o) {
            return o instanceof CompositeGroup.Element e && e.owner() == owner() && e.kind == kind && Arrays.equals(exponents, e.exponents);
        }
        @Override public int hashCode() { return 31*System.identityHashCode(owner())+31*kind.hashCode()+Arrays.hashCode(exponents); }
        @Override public String toString() { return kind + "[" + java.util.HexFormat.of().formatHex(bytes(), 0, Math.min(9, bytes().length)) + "…]"; }
    }
}
