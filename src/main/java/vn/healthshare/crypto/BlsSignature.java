package vn.healthshare.crypto;

import it.unisa.dia.gas.crypto.jpbc.signature.bls01.engines.BLS01Signer;
import it.unisa.dia.gas.crypto.jpbc.signature.bls01.generators.BLS01KeyPairGenerator;
import it.unisa.dia.gas.crypto.jpbc.signature.bls01.generators.BLS01ParametersGenerator;
import it.unisa.dia.gas.crypto.jpbc.signature.bls01.params.BLS01KeyGenerationParameters;
import it.unisa.dia.gas.crypto.jpbc.signature.bls01.params.BLS01PublicKeyParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import vn.healthshare.math.CompositeGroup;
import java.security.SecureRandom;

/** JPBC BLS01 + SHA-256, matching the signature family in section 6.3. */
public final class BlsSignature {
    private final AsymmetricCipherKeyPair keys;
    public record VerificationKey(BLS01PublicKeyParameters parameters){}
    public BlsSignature(CompositeGroup group){
        it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory.getInstance().setUsePBCWhenPossible(false);
        BLS01ParametersGenerator setup=new BLS01ParametersGenerator();setup.init(group.publicPairingParameters());
        BLS01KeyPairGenerator generator=new BLS01KeyPairGenerator();
        generator.init(new BLS01KeyGenerationParameters(new SecureRandom(),setup.generateParameters()));keys=generator.generateKeyPair();
    }
    public VerificationKey verificationKey(){return new VerificationKey((BLS01PublicKeyParameters)keys.getPublic());}
    public byte[] sign(byte[] message){
        CompositeGroup.checkCancelled();
        BLS01Signer signer=new BLS01Signer(new SHA256Digest());signer.init(true,keys.getPrivate());signer.update(message,0,message.length);
        try{byte[] signature=signer.generateSignature();CompositeGroup.checkCancelled();return signature;}catch(CryptoException e){throw new IllegalStateException("BLS không ký được chứng nhận CA.",e);}
    }
    public static boolean verify(byte[] message,byte[] signature,VerificationKey key){
        CompositeGroup.checkCancelled();
        try{
            if(signature.length!=key.parameters().getParameters().getG().getLengthInBytes())return false;
            var point=key.parameters().getParameters().getG().getField().newElementFromBytes(signature);
            if(point.isOne()||!(point instanceof it.unisa.dia.gas.plaf.jpbc.field.curve.CurveElement<?,?> curve)||!curve.isValid()||curve.getY().isZero()||!java.util.Arrays.equals(point.toBytes(),signature))return false;
            BLS01Signer signer=new BLS01Signer(new SHA256Digest());signer.init(false,key.parameters());signer.update(message,0,message.length);boolean valid=signer.verifySignature(signature);CompositeGroup.checkCancelled();return valid;
        }catch(java.util.concurrent.CancellationException e){throw e;}catch(RuntimeException e){return false;}
    }
}
