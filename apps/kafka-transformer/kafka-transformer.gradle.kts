plugins {
    application
    `java-test-fixtures`
}

application {
    applicationName = "app"
    mainClass.set("vanillakotlin.kafkatransformer.AppKt")
}

dependencies {
    implementation(project(":libs:client"))
    testImplementation(libs.okhttp.mock.server)
    testImplementation(testFixtures(project(":libs:common")))
}
