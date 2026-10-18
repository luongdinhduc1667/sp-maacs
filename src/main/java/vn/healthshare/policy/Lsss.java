package vn.healthshare.policy;

import java.math.BigInteger;
import java.util.*;

/** Solve W_I^T * mu = (1,0,...,0) over Z_N using modular Gaussian elimination. */
public final class Lsss {
    private Lsss(){}
    public static List<BigInteger> reconstruct(List<List<BigInteger>> matrix,BigInteger modulus){
        if(matrix.isEmpty()||matrix.getFirst().isEmpty())throw new IllegalArgumentException("Ma trận LSSS rỗng.");
        int variables=matrix.size(),equations=matrix.getFirst().size();
        BigInteger[][] a=new BigInteger[equations][variables+1];
        for(int r=0;r<equations;r++){
            for(int c=0;c<variables;c++){
                if(matrix.get(c).size()!=equations)throw new IllegalArgumentException("Ma trận LSSS không đều.");
                a[r][c]=matrix.get(c).get(r).mod(modulus);
            }
            a[r][variables]=r==0?BigInteger.ONE:BigInteger.ZERO;
        }
        int rank=0;List<Integer> pivots=new ArrayList<>();
        for(int col=0;col<variables&&rank<equations;col++){
            int pivot=-1;
            for(int r=rank;r<equations;r++)if(a[r][col].gcd(modulus).equals(BigInteger.ONE)){pivot=r;break;}
            if(pivot<0)continue;
            BigInteger[] swap=a[rank];a[rank]=a[pivot];a[pivot]=swap;
            BigInteger inverse=a[rank][col].modInverse(modulus);
            for(int c=0;c<=variables;c++)a[rank][c]=a[rank][c].multiply(inverse).mod(modulus);
            for(int r=0;r<equations;r++)if(r!=rank){BigInteger f=a[r][col];for(int c=0;c<=variables;c++)a[r][c]=a[r][c].subtract(f.multiply(a[rank][c])).mod(modulus);}
            pivots.add(col);rank++;
        }
        List<BigInteger> mu=new ArrayList<>(Collections.nCopies(variables,BigInteger.ZERO));
        for(int r=0;r<rank;r++)mu.set(pivots.get(r),a[r][variables]);
        // Verify the solution, including non-unit-pivot systems for which elimination could not solve.
        for(int c=0;c<equations;c++){
            BigInteger sum=BigInteger.ZERO;for(int r=0;r<variables;r++)sum=sum.add(mu.get(r).multiply(matrix.get(r).get(c)));
            if(!sum.mod(modulus).equals(c==0?BigInteger.ONE:BigInteger.ZERO))throw new IllegalArgumentException("Không tái tạo được bí mật từ tập dòng LSSS này.");
        }
        return List.copyOf(mu);
    }
}
