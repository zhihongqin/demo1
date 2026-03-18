import cn.hutool.crypto.digest.BCrypt;
public class GenHash {
    public static void main(String[] args) {
        System.out.println(BCrypt.hashpw("123456"));
    }
}
