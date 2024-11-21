package vanillakotlin.random

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RandomTest {

    @Test fun `random TCIN`() {
        randomTcin().length shouldBe 8
    }

    @Test fun `random string`() {
        randomString(10).length shouldBe 10
    }

    @Test fun `random byte array`() {
        randomByteArray(10).size shouldBe 10
    }
}
