package obscureCRT;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CRTLogic {
    private final List<Integer> moduli = new ArrayList<>();
    private final Random rnd = new Random();

    public void generateKey(int count, int minInclusive, int maxInclusive) {
        moduli.clear();
        while (moduli.size() < count) {
            int val = rnd.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
            if (val < 2) continue;
            if (isPairwiseCoprimeWithList(val, moduli)) moduli.add(val);
        }
    }

    private boolean isPairwiseCoprimeWithList(int n, List<Integer> list) {
        for (int m : list) if (gcd(n, m) != 1) return false;
        return true;
    }

    private int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    public List<Integer> getKey() { return new ArrayList<>(moduli); }

    public void setKeyFromString(String s) {
        moduli.clear();
        if (s == null || s.trim().isEmpty()) return;
        String[] parts = s.split(",");
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            moduli.add(Integer.parseInt(t));
        }
    }

    public int[] getModuliArray() {
        int[] m = new int[moduli.size()];
        for (int i = 0; i < moduli.size(); i++) m[i] = moduli.get(i);
        return m;
    }

    // encode integer (0..255) into residues
    public int[] crtEncode(int value, int[] moduliArray) {
        int[] rem = new int[moduliArray.length];
        for (int i = 0; i < moduliArray.length; i++) rem[i] = value % moduliArray[i];
        return rem;
    }

    // decode residues back into integer using BigInteger CRT
    public int crtDecode(int[] residues, int[] moduliArray) {
        // iterative reconstruction using BigInteger
        BigInteger result = BigInteger.valueOf(residues[0]);
        BigInteger Mprev = BigInteger.valueOf(moduliArray[0]);

        for (int i = 1; i < moduliArray.length; i++) {
            BigInteger mi = BigInteger.valueOf(moduliArray[i]);
            BigInteger ai = BigInteger.valueOf(residues[i]);
            BigInteger rhs = ai.subtract(result).mod(mi);
            BigInteger inv = Mprev.mod(mi).modInverse(mi);
            BigInteger t = (inv.multiply(rhs)).mod(mi);
            result = result.add(Mprev.multiply(t));
            Mprev = Mprev.multiply(mi);
            result = result.mod(Mprev);
        }
        return result.mod(Mprev).intValue();
    }

    // helper: product of moduli (BigInteger)
    public BigInteger productOfModuli(int[] moduliArray) {
        BigInteger p = BigInteger.ONE;
        for (int m : moduliArray) p = p.multiply(BigInteger.valueOf(m));
        return p;
    }

    // helper: compute bits per modulus
    public int[] bitsPerMod(int[] moduliArray) {
        int[] bits = new int[moduliArray.length];
        for (int i = 0; i < moduliArray.length; i++) {
            bits[i] = Math.max(1, (int) Math.ceil(Math.log(moduliArray[i]) / Math.log(2.0)));
        }
        return bits;
    }
}