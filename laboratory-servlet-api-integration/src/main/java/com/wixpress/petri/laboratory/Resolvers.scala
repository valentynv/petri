package com.wixpress.petri.laboratory

import java.util.UUID
import javax.servlet.http.HttpServletRequest

import com.wixpress.petri.laboratory.HttpRequestExtractionOptions.{Cookie, Header, Param}

trait Converter[T] {
  def convert(value: String): T
}

abstract class Resolver[T >: Null] {
  val filterParam: FilterParameters.Value

  def defaultResolution(request: HttpServletRequest): String

  def convert(value: String): T

  def resolve(request: HttpServletRequest, filterParametersConfig: FilterParametersConfig): T = {
    val extractorConfig = filterParametersConfig.extractors.get(filterParam.toString)

    val extractedByConfig = extractorConfig.flatMap(_.collectFirst {
      case config if extractBy(request, config).isDefined => extractBy(request, config)
    }).flatten

    val extractedValue = extractedByConfig.getOrElse(defaultResolution(request))

    Option(extractedValue).map(convertValue(_, filterParametersConfig.converters)).orNull
  }

  private def extractBy(request: HttpServletRequest, config: (String, String)): Option[String] = {
    val header = Header.toString
    val cookie = Cookie.toString
    val param = Param.toString

    val extractedValue = config match {
      case (`header`, name) => Option(request.getHeader(name))
      case (`cookie`, name) => Option(request.getCookies).flatMap(_.find(_.getName == name).map(_.getValue))
      case (`param`, name) => Option(request.getParameter(name))
      case _ => None
    }
    extractedValue
  }

  private def convertValue(extractedValue: String, converters: Map[String, Converter[_]]): T = {

    val converter = converters.get(filterParam.toString).asInstanceOf[Option[Converter[T]]]
    converter.map(_.convert(extractedValue)).getOrElse(convert(extractedValue))
  }
}

trait StringResolver extends Resolver[String] {
  override def convert(value: String): String = value
}

class CountryResolver extends StringResolver {
  override val filterParam = FilterParameters.Country

  override def defaultResolution(request: HttpServletRequest): String =
    Option(request.getHeader("GEOIP_COUNTRY_CODE")).getOrElse(request.getLocale.getCountry)
}

object CountryResolver {
  def apply(): CountryResolver = new CountryResolver
}

class LanguageResolver extends StringResolver {
  override val filterParam = FilterParameters.Language

  override def defaultResolution(request: HttpServletRequest): String = request.getLocale.getLanguage
}

object LanguageResolver {
  def apply(): LanguageResolver = new LanguageResolver()
}

class UserIdResolver extends Resolver[UUID] {
  override val filterParam = FilterParameters.UserId

  override def convert(value: String): UUID = {
    if (value == null) null
    else UUID.fromString(value)
  }

  override def defaultResolution(request: HttpServletRequest): String =
    Option(request.getParameter("laboratory_user_id")).orNull
}

object UserIdResolver {
  def apply(): UserIdResolver = new UserIdResolver()
}