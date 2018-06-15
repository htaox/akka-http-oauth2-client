package com.github.dakatsuka.akka.http.oauth2.client
import scala.language.postfixOps
import java.net.URI

import akka.http.scaladsl.{ Http, HttpsConnectionContext }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.Flow
import com.typesafe.sslconfig.akka.AkkaSSLConfig
// import com.typesafe.sslconfig.ssl.{ TrustManagerConfig, TrustStoreConfig }
import java.security.cert.X509Certificate

import javax.net.ssl.{ KeyManager, SSLContext, X509TrustManager }
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import org.scalatest.{ FunSpec, Matchers }

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

class Client2Spec extends FunSpec with Matchers {

  implicit val system: ActorSystem        = ActorSystem()
  implicit val ec: ExecutionContext       = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  private val trustfulSslContext: SSLContext = {

    object NoCheckX509TrustManager extends X509TrustManager {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String) = ()

      override def checkServerTrusted(chain: Array[X509Certificate], authType: String) = ()

      override def getAcceptedIssuers = Array[X509Certificate]()
    }

    val context = SSLContext.getInstance("TLS")
    context.init(Array[KeyManager](), Array(NoCheckX509TrustManager), null)
    context
  }

  describe("") {

    it("With bad akka https context") {
      // val trustStoreConfig   = TrustStoreConfig(data = None, filePath = Some("/home/htao/security/certificate.cer")).withStoreType("PEM")
      // val trustManagerConfig = TrustManagerConfig().withTrustStoreConfigs(List(trustStoreConfig))

      // https://gist.github.com/iRevive/7d17144284a7a2227487635ec815860d
      // https://stackoverflow.com/questions/47349020/how-to-make-a-post-call-to-self-certified-server-with-akka-http
      val badSslConfig = AkkaSSLConfig().mapSettings(
        s =>
          s.withLoose(
            s.loose
              .withAcceptAnyCertificate(true)
              .withDisableHostnameVerification(true)
        )
        // .withTrustManagerConfig(trustManagerConfig)
      )
      val ctx = Http().createClientHttpsContext(badSslConfig) // here you get initialized context (ssl params, etc)

      // val noCertificateCheckContext = ConnectionContext.https(trustfulSslContext)
      //copy everything except ssl context
      val httpsCtx = new HttpsConnectionContext(
        trustfulSslContext,
        ctx.sslConfig,
        ctx.enabledCipherSuites,
        ctx.enabledProtocols,
        ctx.clientAuth,
        ctx.sslParameters
      )

      val connection: Option[Flow[HttpRequest, HttpResponse, _]] =
        Some(Http().outgoingConnectionHttps("localhost", 4444, connectionContext = httpsCtx))

      // val config = Config("axelrod", "9876543210", URI.create("https://localhost:4444"))
      val config = Config("axelrod", "9876543210", URI.create(""))
      val client = new Client(config, connection)

      val f = client.getAccessToken(GrantType.ClientCredentials)

      val result = Await.result(f, 10 second)

      if (result.isRight) println(result.right) else println(result.left)

      println("Done")

    }

  }

}
