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
    implementation(project(":libs:http4k"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:common"))
    implementation(project(":libs:metrics"))
    testImplementation(libs.okhttp.mock.server)
    testImplementation(testFixtures(project(":libs:common")))
}
