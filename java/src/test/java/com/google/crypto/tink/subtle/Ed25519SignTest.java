// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.subtle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.crypto.tink.TestUtil;
import java.security.GeneralSecurityException;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link Ed25519Sign}.
 *
 * <p>TODO(quannguyen): Use Github Wycheproof's test vectors once it's available (b/66825199).
 */
@RunWith(JUnit4.class)
public final class Ed25519SignTest {
  @Test
  public void testSigningOneKeyWithMultipleMessages() throws Exception {
    Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPair();
    Ed25519Sign signer = new Ed25519Sign(keyPair.getPrivateKey());
    Ed25519Verify verifier = new Ed25519Verify(keyPair.getPublicKey());
    for (int i = 0; i < 1024; i++) {
      byte[] msg = Random.randBytes(1024);
      byte[] sig = signer.sign(msg);
      try {
        verifier.verify(sig, msg);
      } catch (GeneralSecurityException ex) {
        fail(
            String.format(
                "\n\nMessage: %s\nSignature: %s\nPrivateKey: %s\nPublicKey: %s\n",
                TestUtil.hexEncode(msg),
                TestUtil.hexEncode(sig),
                TestUtil.hexEncode(keyPair.getPrivateKey()),
                TestUtil.hexEncode(keyPair.getPublicKey())));
      }
    }
  }

  @Test
  public void testSigningOneKeyWithTheSameMessage() throws Exception {
    Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPair();
    Ed25519Sign signer = new Ed25519Sign(keyPair.getPrivateKey());
    Ed25519Verify verifier = new Ed25519Verify(keyPair.getPublicKey());
    byte[] msg = Random.randBytes(1024);
    TreeSet<String> allSignatures = new TreeSet<String>();
    for (int i = 0; i < 1024; i++) {
      byte[] sig = signer.sign(msg);
      allSignatures.add(TestUtil.hexEncode(sig));
      try {
        verifier.verify(sig, msg);
      } catch (GeneralSecurityException ex) {
        fail(
            String.format(
                "\n\nMessage: %s\nSignature: %s\nPrivateKey: %s\nPublicKey: %s\n",
                TestUtil.hexEncode(msg),
                TestUtil.hexEncode(sig),
                TestUtil.hexEncode(keyPair.getPrivateKey()),
                TestUtil.hexEncode(keyPair.getPublicKey())));
      }
    }
    // Ed25519 is deterministic, expect a unique signature for the same message.
    assertEquals(1, allSignatures.size());
  }

  @Test
  public void testSignWithPrivateKeyLengthDifferentFrom32Byte() throws Exception {
    try {
      Ed25519Sign unused = new Ed25519Sign(new byte[31]);
      fail("Private key length should be 32-byte");
    } catch (IllegalArgumentException expected) {
    }
    try {
      Ed25519Sign unused = new Ed25519Sign(new byte[33]);
      fail("Private key length should be 32-byte");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSigningWithMultipleRandomKeyAndMessages() throws Exception {
    for (int i = 0; i < 10000; i++) {
      Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPair();
      Ed25519Sign signer = new Ed25519Sign(keyPair.getPrivateKey());
      Ed25519Verify verifier = new Ed25519Verify(keyPair.getPublicKey());
      byte[] msg = Random.randBytes(1024);
      byte[] sig = signer.sign(msg);
      try {
        verifier.verify(sig, msg);
      } catch (GeneralSecurityException ex) {
        fail(
            String.format(
                "\n\nMessage: %s\nSignature: %s\nPrivateKey: %s\nPublicKey: %s\n",
                TestUtil.hexEncode(msg),
                TestUtil.hexEncode(sig),
                TestUtil.hexEncode(keyPair.getPrivateKey()),
                TestUtil.hexEncode(keyPair.getPublicKey())));
      }
    }
  }

  @Test
  public void testSigningWithWycheproofVectors() throws Exception {
    if (TestUtil.isAndroid()) {
      System.out.println("testSigningWithWycheproofVectors doesn't work on Android, skipping");
      return;
    }

    JSONObject json = TestUtil.readJson("testdata/wycheproof/eddsa_test.json");
    checkAlgAndVersion(json);
    int numTests = json.getInt("numberOfTests");
    int cntTests = 0;
    int cntSkippedTests = 0;
    int errors = 0;
    JSONArray testGroups = json.getJSONArray("testGroups");
    for (int i = 0; i < testGroups.length(); i++) {
      JSONObject group = testGroups.getJSONObject(i);
      JSONObject key = group.getJSONObject("key");
      byte[] privateKey = Hex.decode(key.getString("sk"));
      JSONArray tests = group.getJSONArray("tests");
      for (int j = 0; j < tests.length(); j++) {
        JSONObject testcase = tests.getJSONObject(j);
        int tcId = testcase.getInt("tcId");
        String tc = "tcId: " + tcId + " " + testcase.getString("comment");
        byte[] msg = Hex.decode(testcase.getString("message"));
        byte[] sig = Hex.decode(testcase.getString("sig"));
        String result = testcase.getString("result");
        if (result.equals("invalid")) {
          cntSkippedTests++;
          continue;
        }
        Ed25519Sign signer = new Ed25519Sign(privateKey);
        byte[] computedSig = signer.sign(msg);
        assertArrayEquals("Test case: " + tc, sig, computedSig);
        cntTests++;
      }
    }
    assertEquals(0, errors);
    assertEquals(numTests, cntTests + cntSkippedTests);
  }

  private void checkAlgAndVersion(JSONObject jsonObj) throws Exception {
    final String expectedAlgorithm = "EDDSA";
    String algorithm = jsonObj.getString("algorithm");
    if (!expectedAlgorithm.equals(algorithm)) {
      System.out.println("expect algorithm " + expectedAlgorithm + ", got" + algorithm);
    }
    final String expectedVersion = "0.0a18";
    String generatorVersion = jsonObj.getString("generatorVersion");
    if (!generatorVersion.equals(expectedVersion)) {
      System.out.println(
          "expect test vectors with version "
              + expectedVersion
              + " ,got vectors with version "
              + generatorVersion);
    }
  }
}
