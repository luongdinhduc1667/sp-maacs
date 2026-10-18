package vn.healthshare;
import org.junit.Test;
import vn.healthshare.policy.Lsss;
import java.math.BigInteger;
import java.util.List;
import static org.junit.Assert.*;

public final class LsssReconstructionTest {
    private static final BigInteger N=BigInteger.valueOf(101*103*107);
    @Test public void nontrivialReconstructionCoefficients(){
        var matrix=List.of(List.of(BigInteger.ONE,BigInteger.TWO),List.of(BigInteger.ONE,BigInteger.valueOf(3)));
        assertEquals(List.of(BigInteger.valueOf(3),N.subtract(BigInteger.TWO)),Lsss.reconstruct(matrix,N));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnsatisfiedSubset(){Lsss.reconstruct(List.of(List.of(BigInteger.ONE,BigInteger.ONE)),N);}
    @Test public void orRouteNeedsOnlyOneRow(){assertEquals(List.of(BigInteger.ONE),Lsss.reconstruct(List.of(List.of(BigInteger.ONE,BigInteger.ZERO)),N));}
}
