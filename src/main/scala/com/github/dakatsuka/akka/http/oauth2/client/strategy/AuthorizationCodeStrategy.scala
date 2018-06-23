package com.github.dakatsuka.akka.http.oauth2.client.strategy

import akka.NotUsed
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{ Flow, Source }
import com.github.dakatsuka.akka.http.oauth2.client.Error.UnauthorizedException
import com.github.dakatsuka.akka.http.oauth2.client.{ ConfigLike, Error, GrantType }

object AuthorizationCodeStrategy {
  val tryGetAuthCode = Flow[HttpResponse].map { resp =>
    resp.status match {
      case StatusCodes.OK =>
        val locationHeader = resp.getHeader("location")

        if (locationHeader.isPresent) {
          val location = locationHeader.get().value()
          val authCode = Uri(location).query().getOrElse("code", "")

          if (authCode.length > 0)
            authCode
          else
            throw new NoSuchElementException("Code is not found in the querystring.")
        } else {
          throw new NoSuchElementException("Location header missing.")
        }
      case x =>
        throw new UnauthorizedException(Error.InvalidClient, "Unauthorized client.", resp)
    }
  }
}

class AuthorizationCodeStrategy extends Strategy(GrantType.AuthorizationCode) {
  override def getAuthorizeUrl(config: ConfigLike, params: Map[String, String] = Map.empty): Option[Uri] = {
    val uri = Uri
      .apply(config.site.toASCIIString)
      .withPath(Uri.Path(config.authorizeUrl))
      .withQuery(Uri.Query(params ++ Map("response_type" -> "code", "client_id" -> config.clientId)))

    Option(uri)
  }

  def getAuthorizationCodeSource(url: Uri): Source[HttpRequest, NotUsed] = {
    val request = HttpRequest(method = HttpMethods.GET,
                              uri = url,
                              headers = List(
                                RawHeader("Accept", "*/*")
                              ))

    Source.single(request)
  }

  override def getAccessTokenSource(config: ConfigLike, params: Map[String, String] = Map.empty): Source[HttpRequest, NotUsed] = {
    require(params.contains("code"))
    require(params.contains("redirect_uri"))

    val uri = Uri
      .apply(config.site.toASCIIString)
      .withPath(Uri.Path(config.tokenUrl))

    val request = HttpRequest(
      method = config.tokenMethod,
      uri = uri,
      headers = List(
        RawHeader("Accept", "*/*")
      ),
      FormData(
        params ++ Map(
          "grant_type"    -> grant.value,
          "client_id"     -> config.clientId,
          "client_secret" -> config.clientSecret
        )
      ).toEntity(HttpCharsets.`UTF-8`)
    )

    Source.single(request)
  }
}
