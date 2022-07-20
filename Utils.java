import java.io.*;
import java.security.*;
import org.apache.commons.lang3.SerializationUtils;

class Utils {
    public static byte[] serializeObject(Serializable obj) {
        byte[] data = SerializationUtils.serialize(obj);
        return data;
    }

    public static Object deserializeObject(byte[] data) {
        Object deserialized = SerializationUtils.deserialize(data);
        return deserialized;
    }

    public static String sha1Hash(byte[] object) {
        try {
          byte[] hash = new byte[20];
          MessageDigest md = MessageDigest.getInstance("SHA-1");
          hash = md.digest(object);
          return new String(hash);
        } catch (NoSuchAlgorithmException nsae) {
            throw new IllegalArgumentException("System does not support SHA-1");
        }
      }

    public static void writeObjectToFile(Object obj, String filePath) {
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(obj);
            oos.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
}
