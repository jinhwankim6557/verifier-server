import java.util.Base64;
import org.omnione.did.oid4vc.oid4vp.util.crypto.MultibaseUtils;

public class SignHelper {
    public static void main(String[] args) throws Exception {
        String key = "z3QtUYcvXwoGnBByPL9e8H1xa9x8EmRRkm9YaGgHNt2RYYJYb4nXNuaAxmM5mcmbCFZPwY7N6kDuFEsWvg6hkAKtzgBMvWYfET2r21hZerewocjUJNdeBfAsPWyHG4PpFvkJJE4Bc8YNbz4r8jj4L4XFSshrBRUsHuLztwNHdFqXBcDjQoEBV6nLq4zoUooNmibPwwXKh7uKfpV4Ckwe23hfNHmq";
        byte[] decoded = MultibaseUtils.decodeMultibase(key);
        System.out.println("Length: " + decoded.length);
        System.out.print("First 10 bytes: ");
        for(int i=0; i<10; i++){
            System.out.printf("%02x ", decoded[i]);
        }
        System.out.println();
    }
}
