package org.freedomtool.utils

import android.content.Context
import com.google.gson.Gson
import org.freedomtool.data.models.Proof
import org.freedomtool.data.models.ZkProof
import java.io.ByteArrayOutputStream


class ZKPTools(val context: Context) {

    external fun voteSMT(circuitBuffer: ByteArray,
                         circuitSize: Long,
                         jsonBuffer: ByteArray,
                         jsonSize: Long,
                         wtnsBuffer: ByteArray,
                         wtnsSize: LongArray,
                         errorMsg: ByteArray,
                         errorMsgMaxSize: Long): Int
    external fun credentialAtomicQueryMTPV2OnChainVoting(circuitBuffer: ByteArray,
                                                       circuitSize: Long,
                                                       jsonBuffer: ByteArray,
                                                       jsonSize: Long,
                                                       wtnsBuffer: ByteArray,
                                                       wtnsSize: LongArray,
                                                       errorMsg: ByteArray,
                                                       errorMsgMaxSize: Long): Int

    external fun witnesscalcAuthV2(circuitBuffer: ByteArray,
                               circuitSize: Long,
                               jsonBuffer: ByteArray,
                               jsonSize: Long,
                               wtnsBuffer: ByteArray,
                               wtnsSize: LongArray,
                               errorMsg: ByteArray,
                               errorMsgMaxSize: Long): Int
    external fun passportVerification1(circuitBuffer: ByteArray,
                                         circuitSize: Long,
                                         jsonBuffer: ByteArray,
                                         jsonSize: Long,
                                         wtnsBuffer: ByteArray,
                                         wtnsSize: LongArray,
                                         errorMsg: ByteArray,
                                         errorMsgMaxSize: Long) : Int
    external fun passportVerification256(circuitBuffer: ByteArray,
                                      circuitSize: Long,
                                      jsonBuffer: ByteArray,
                                      jsonSize: Long,
                                      wtnsBuffer: ByteArray,
                                      wtnsSize: LongArray,
                                      errorMsg: ByteArray,
                                      errorMsgMaxSize: Long) : Int

    external fun CalcPublicBufferSize(zkeyBuffer: ByteArray, zkeySize: Long): Long
    external fun groth16_prover(
        zkeyBuffer: ByteArray, zkeySize: Long,
        wtnsBuffer: ByteArray, wtnsSize: Long,
        proofBuffer: ByteArray, proofSize: LongArray,
        publicBuffer: ByteArray, publicSize: LongArray,
        errorMsg: ByteArray, errorMsgMaxSize: Long
    ): Int

    init {
        System.loadLibrary("rapidsnark");
        System.loadLibrary("freedomtool")
    }

    fun openRawResourceAsByteArray(resourceName: Int): ByteArray {
        val inputStream = context.resources.openRawResource(resourceName)
        val byteArrayOutputStream = ByteArrayOutputStream()

        try {
            val buffer = ByteArray(1024)
            var length: Int

            while (inputStream.read(buffer).also { length = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, length)
            }

            // Process the content as needed

            return byteArrayOutputStream.toByteArray()
        } finally {
            // Close the streams in a finally block to ensure they are closed even if an exception occurs
            byteArrayOutputStream.close()
            inputStream.close()
        }
    }
}

class ZKPUseCase(val context: Context) {


    fun generateZKP(
        zkpId: Int, datFile: Int, inputs: ByteArray, proofFunction: (
            circuitBuffer: ByteArray,
            circuitSize: Long,
            jsonBuffer: ByteArray,
            jsonSize: Long,
            wtnsBuffer: ByteArray,
            wtnsSize: LongArray,
            errorMsg: ByteArray,
            errorMsgMaxSize: Long
        ) -> Int
    ): ZkProof {
        val zkpTool = ZKPTools(context)
        val zkp = zkpTool.openRawResourceAsByteArray(zkpId)
        val datFile = zkpTool.openRawResourceAsByteArray(datFile)

        val msg = ByteArray(256)

        val witnessLen = LongArray(1)
        witnessLen[0] = 100 * 1024 * 1024

        val byteArr = ByteArray(100 * 1024 * 1024)

        val res = proofFunction(
            datFile,
            datFile.size.toLong(),
            inputs,
            inputs.size.toLong(),
            byteArr,
            witnessLen,
            msg,
            256
        )

        if (res == 2) {
            throw Exception("Not enough memory for zkp")
        }

        if (res == 1) {
            throw Exception("Error during zkp ${msg.decodeToString()}")
        }

        val pubData = ByteArray(4 *1024 *1024)


        val pubLen = LongArray(1)
        pubLen[0] = pubData.size.toLong()

        val proofData = ByteArray(4*1024*1024)
        val proofLen = LongArray(1)
        proofLen[0] = proofData.size.toLong()

        val witnessData = byteArr.copyOfRange(0, witnessLen[0].toInt())

        val verification = zkpTool.groth16_prover(
            zkp,
            zkp.size.toLong(),
            witnessData,
            witnessLen[0],
            proofData,
            proofLen,
            pubData,
            pubLen,
            msg,
            256
        )

        if (verification == 2) {
            throw Exception("Not enough memory for verification ${msg.decodeToString()}")
        }

        if (verification == 1) {
            throw Exception("Error during verification ${msg.decodeToString()}")
        }

        val proofDataZip = proofData.copyOfRange(0, proofLen[0].toInt())

        val index = findLastIndexOfSubstring(
            proofDataZip.toString(Charsets.UTF_8),
            "\"protocol\":\"groth16\"}"
        )
        val indexPubData = findLastIndexOfSubstring(
            pubData.decodeToString(),
            "]"
        )

        val formatedPubData = pubData.decodeToString().slice(0..indexPubData)

        val foramtedProof = proofDataZip.toString(Charsets.UTF_8).slice(0..index)
        val proof = Proof.fromJson(foramtedProof)

        return ZkProof(
            proof = proof,
            pub_signals = getPubSignals(formatedPubData).toList()
        )
    }

    private fun findLastIndexOfSubstring(mainString: String, searchString: String): Int {
        val index = mainString.lastIndexOf(searchString)

        if (index != -1) {
            // If substring is found, calculate the last index of the substring
            return index + searchString.length - 1
        }

        return -1
    }

    private fun getPubSignals(jsonString: String): List<String> {
        val gson = Gson()
        val stringArray = gson.fromJson(jsonString, Array<String>::class.java)
        return stringArray.toList()
    }
}