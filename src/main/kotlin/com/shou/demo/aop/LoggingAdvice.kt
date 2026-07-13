package com.shou.demo.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

// ロガーの生成
// info、error などログレベルごとのメソッドが用意されており、ログレベルに応じて出力するログを制御することができる
private val logger = LoggerFactory.getLogger(LoggingAdvice::class.java)

// クラス名に含まれる Advice(アドバイス) は、AOP において「横断的関心事」の処理を定義するものを指す
// @Aspect アノテーションを付与することで、AOP の対象となるクラスであることを示す
@Aspect
@Component
class LoggingAdvice {
    // @Before アノテーションを付与することで、対象メソッドの実行前に処理を実行することを示す
    // execution で対象の関数を指定する
    // execution(戻り値 パッケージ名.クラス名.メソッド名(引数)) で対象メソッドを指定する
    // * はワイルドカードで、任意の値を表す
    // JoinPoint には、この Before、After の処理が呼び出される対象の処理（ここでは Controller クラスの処理）の情報が格納される
    @Before("execution(* com.shou.demo.usecase..*.*(..))")
    fun beforeLog(joinPoint: JoinPoint) {
        val user = SecurityContextHolder.getContext().authentication.asBookManagerUserDetails()
        // ログレベル INFO でログを出力する
        logger.info("Start: ${joinPoint.signature} userId=${user?.id}")
        logger.info("Class: ${joinPoint.target.javaClass}")
        logger.info(
            "Session: ${(RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request.session.id}",
        )
    }

    // @After アノテーションを付与することで、対象メソッドの実行後に処理を実行することを示す
    @After("execution(* com.shou.demo.usecase..*.*(..))")
    fun afterLog(joinPoint: JoinPoint) {
        val user = SecurityContextHolder.getContext().authentication.asBookManagerUserDetails()
        logger.info("End: ${joinPoint.signature} userId=${user?.id}")
    }

    @Around("execution(* com.shou.demo.usecase..*.*(..))")
    fun aroundLog(joinPoint: ProceedingJoinPoint): Any? {
        val user = SecurityContextHolder.getContext().authentication.asBookManagerUserDetails()
        logger.info("Start Proceed: ${joinPoint.signature} userId=${user?.id}")

        val result = joinPoint.proceed()

        logger.info("End Proceed: ${joinPoint.signature} userId=${user?.id}")
        return result
    }

    @AfterReturning("execution(* com.shou.demo.usecase..*.*(..))")
    fun afterReturningLog(
        joinPoint: JoinPoint,
        returnValue: Any?,
    ) {
        logger.info("After Returning: ${joinPoint.signature} returnValue=$returnValue")
    }

    @AfterThrowing(pointcut = "execution(* com.shou.demo.usecase..*.*(..))", throwing = "e")
    fun afterThrowingLog(
        joinPoint: JoinPoint,
        e: Throwable,
    ) {
        logger.error("Exception: ${joinPoint.signature} exception=${e.message}")
    }
}
