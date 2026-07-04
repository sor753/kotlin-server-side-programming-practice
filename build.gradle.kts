plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("idea")
	id("nu.studer.jooq") version "9.0"
}

idea {
	module {
		isDownloadSources = true
		isDownloadJavadoc = true
	}
}

group = "com.shou"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

// Spring Boot の依存管理が org.jooq を Java 21 必須のバージョンへ引き上げるため、
// JDK 17 と互換性のあるバージョンに固定する
dependencyManagement {
	dependencies {
		dependency("org.jooq:jooq:3.19.15")
		dependency("org.jooq:jooq-meta:3.19.15")
		dependency("org.jooq:jooq-codegen:3.19.15")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("com.mysql:mysql-connector-j")
	jooqGenerator("com.mysql:mysql-connector-j")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
	sourceSets {
		main {
			kotlin.srcDir("build/generated-src/jooq/main")
		}
	}
}

jooq {
	version.set("3.19.15")
	configurations {
		create("main") {
			generateSchemaSourceOnCompilation.set(true)
			jooqConfiguration.apply {
				jdbc.apply {
					driver = "com.mysql.cj.jdbc.Driver"
					url = "jdbc:mysql://mysql:3306/demo"
					user = "user"
					password = "password"
				}
				generator.apply {
					name = "org.jooq.codegen.KotlinGenerator"
					database.apply {
						name = "org.jooq.meta.mysql.MySQLDatabase"
						inputSchema = "demo"
					}
					target.apply {
						packageName = "com.shou.demo.jooq"
						directory = "build/generated-src/jooq/main"
					}
				}
			}
		}
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
