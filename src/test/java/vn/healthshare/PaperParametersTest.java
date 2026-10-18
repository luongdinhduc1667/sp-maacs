package vn.healthshare;

import org.junit.Assume;
import org.junit.Test;
import vn.healthshare.math.CompositeGroup;
import vn.healthshare.sim.Simulation;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;
import vn.healthshare.crypto.SpMaacs;
import java.util.*;
import static org.junit.Assert.*;

public final class PaperParametersTest {
    @Test public void actualThree517BitPrimesBlsAndAbeRoundTrip()throws Exception{
        Assume.assumeTrue(Boolean.getBoolean("paper.tests"));
        Simulation sim=new Simulation(517,System.out::println);var g=sim.scheme().group();
        assertArrayEquals(new int[]{517,517,517},g.primeBitLengths());assertTrue(g.order().bitLength()>=1549);
        Attribute a=Attribute.of("AA","Role","Doctor");sim.addAuthority("AA",List.of(a));sim.register("paper-user");sim.issue("paper-user",List.of(a));
        var identity=sim.users().get("paper-user").identity();assertTrue(SpMaacs.verifyIdentity(identity,sim.ca().verificationKey()));
        assertFalse(sim.ca().verificationKey().parameters().getParameters().getParameters().containsKey("n0"));
        var k=g.targetGenerator().pow(g.p1Scalar());var ct=sim.scheme().encapsulate(k,Policy.parse("AA.Role=Doctor"),sim.publicKeys(),System.out::println);
        assertEquals(k,sim.scheme().recoverCandidate(ct.getFirst(),sim.users().get("paper-user")));
        byte[] bad=identity.signature();Arrays.fill(bad,(byte)0);assertFalse(SpMaacs.verifyIdentity(new SpMaacs.Identity(identity.gid(),identity.k1(),bad),sim.ca().verificationKey()));
        System.out.println("PASS actual JPBC Type A1 / three 517-bit primes / BLS01 / Encrypt-Decrypt K.");
    }
}
