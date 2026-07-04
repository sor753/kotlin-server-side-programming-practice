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

// Spring Boot の依存管理が org.jooq / org.flywaydb を Java 21 必須のバージョンへ引き上げるため、
// JDK 17 と互換性のあるバージョンに固定する
dependencyManagement {
	dependencies {
		dependency("org.jooq:jooq:3.19.15")
		dependency("org.jooq:jooq-meta:3.19.15")
		dependency("org.jooq:jooq-codegen:3.19.15")
		dependency("org.flywaydb:flyway-core:10.20.0")
		dependency("org.flywaydb:flyway-mysql:10.20.0")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")
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
						excludes = "flyway_schema_history"
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

// 公式の flyway Gradle プラグインが Gradle 9 と非互換(JavaPluginConventionが削除済み)なため、
// flyway-commandline を JavaExec で直接叩く自前タスクで代替する
val flywayCli by configurations.creating

dependencies {
	// MySQL以外のDB用モジュールは不要な上、一部が flyway-core と噛み合わないバージョンに
	// 解決されてしまうため除外する
	flywayCli("org.flywaydb:flyway-commandline:10.20.0") {
		exclude(group = "org.flywaydb", module = "flyway-gcp-bigquery")
		exclude(group = "org.flywaydb", module = "flyway-gcp-spanner")
		exclude(group = "org.flywaydb", module = "flyway-sqlserver")
		exclude(group = "org.flywaydb", module = "flyway-database-oracle")
		exclude(group = "org.flywaydb", module = "flyway-database-db2")
		exclude(group = "org.flywaydb", module = "flyway-database-derby")
		exclude(group = "org.flywaydb", module = "flyway-database-hsqldb")
		exclude(group = "org.flywaydb", module = "flyway-database-informix")
		exclude(group = "org.flywaydb", module = "flyway-database-postgresql")
		exclude(group = "org.flywaydb", module = "flyway-database-saphana")
		exclude(group = "org.flywaydb", module = "flyway-database-snowflake")
		exclude(group = "org.flywaydb", module = "flyway-database-redshift")
		exclude(group = "org.flywaydb", module = "flyway-database-sybasease")
		exclude(group = "org.flywaydb", module = "flyway-firebird")
		exclude(group = "org.flywaydb", module = "flyway-singlestore")
		exclude(group = "org.flywaydb", module = "flyway-database-cassandra")
		exclude(group = "org.flywaydb", module = "flyway-database-ignite")
		exclude(group = "org.flywaydb", module = "flyway-database-tidb")
		exclude(group = "org.flywaydb", module = "flyway-database-yugabytedb")
		exclude(group = "org.flywaydb", module = "flyway-database-clickhouse")
		exclude(group = "org.flywaydb", module = "flyway-database-oceanbase")
		exclude(group = "org.flywaydb", module = "flyway-database-databricks")
		exclude(group = "org.flywaydb", module = "flyway-database-db2zos")
	}
	flywayCli("org.flywaydb:flyway-mysql:10.20.0")
	flywayCli("com.mysql:mysql-connector-j")
}

tasks.register<JavaExec>("flywayMigrate") {
	classpath = flywayCli
	mainClass.set("org.flywaydb.commandline.Main")
	args(
		"-url=jdbc:mysql://mysql:3306/demo",
		"-user=user",
		"-password=password",
		"-locations=filesystem:src/main/resources/db/migration",
		"migrate",
	)
}

// jOOQのコード生成は実際のDBスキーマを読み取るため、
// 生成前に必ずFlywayでマイグレーションを最新まで適用しておく
tasks.named("generateJooq") {
	dependsOn("flywayMigrate")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
