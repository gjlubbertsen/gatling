/**
 * Copyright 2011-2013 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.excilys.ebi.gatling.recorder.config

import scala.collection.JavaConversions.{asScalaBuffer, mapAsJavaMap }
import scala.collection.mutable
import scala.tools.nsc.io.File
import scala.tools.nsc.io.Path.string2path

import com.excilys.ebi.gatling.core.config.{GatlingConfiguration, GatlingFiles}
import com.excilys.ebi.gatling.core.util.StringHelper.trimToOption
import com.excilys.ebi.gatling.recorder.config.ConfigurationConstants._
import com.excilys.ebi.gatling.recorder.ui.enumeration.{PatternType, FilterStrategy}
import com.excilys.ebi.gatling.recorder.ui.enumeration.FilterStrategy.FilterStrategy
import com.excilys.ebi.gatling.recorder.ui.enumeration.PatternType.PatternType

import grizzled.slf4j.Logging
import com.typesafe.config.{ Config, ConfigFactory, ConfigRenderOptions }

case class Pattern(patternType: PatternType, pattern: String)

object RecorderConfiguration  extends Logging {

	var saveConfiguration = false

	val renderOptions = ConfigRenderOptions.concise.setFormatted(true)

	val configFile = File(System.getProperty("user.home") / "gatling-recorder.conf")

	var configuration: RecorderConfiguration = _

	GatlingConfiguration.setUp()

	def initialSetup(props: mutable.Map[String, Any]) {
		val classLoader = getClass.getClassLoader
		val defaultsConfig = ConfigFactory.parseResources(classLoader,"recorder-defaults.conf")
		val customConfig = getCustomConfig
		val propertiesConfig = ConfigFactory.parseMap(props)
		buildConfig(propertiesConfig.withFallback(customConfig).withFallback(defaultsConfig))
		debug(configuration)
	}

	def reload(props: mutable.Map[String, Any]) {
		val frameConfig = ConfigFactory.parseMap(props)
		buildConfig(frameConfig.withFallback(configuration.config))
		debug(configuration)
	}

	def saveConfig {
		// Request bodies is transient => remove the key before saving the new configuration
		val configToSave = configuration.config.root.withoutKey(REQUEST_BODIES_FOLDER)
		configFile.writeAll(configToSave.render(renderOptions))
	}

	def getCustomConfig = if (configFile.exists) {
		saveConfiguration = true
		ConfigFactory.parseFile(configFile.jfile)
	} else ConfigFactory.empty

	def buildConfig(config: Config) {
		def zeroToOption(value: Int) = if(value != 0) Some(value) else None

		def buildPatterns(patterns: List[String], patternsType: List[String]) = {
			patterns.zip(patternsType).map{ case (pattern, patternType) => Pattern(PatternType.withName(patternType),pattern) }
		}
		def getOutputFolder(folder: String) = {
			trimToOption(folder).getOrElse(Option(System.getenv("GATLING_HOME")).map(_ => GatlingFiles.sourcesDirectory.toString).getOrElse(System.getProperty("user.home")))
		}

		def getRequestBodiesFolder =
			if (config.hasPath(REQUEST_BODIES_FOLDER))
				config.getString(REQUEST_BODIES_FOLDER)
			else GatlingFiles.requestBodiesDirectory.toString

		configuration = RecorderConfiguration(
			filters = FiltersConfiguration(
				filterStrategy = FilterStrategy.withName(config.getString(FILTER_STRATEGY)),
				patterns = buildPatterns(config.getStringList(PATTERNS).toList,config.getStringList(PATTERNS_TYPE).toList)),
			http = HttpConfiguration(
				automaticReferer = config.getBoolean(AUTOMATIC_REFERER),
				followRedirect = config.getBoolean(FOLLOW_REDIRECT)),
			proxy = ProxyConfiguration(
				port = config.getInt(LOCAL_PORT),
				sslPort = config.getInt(LOCAL_SSL_PORT),
				outgoing = OutgoingProxyConfiguration(
					host = trimToOption(config.getString(PROXY_HOST)),
					username = trimToOption(config.getString(PROXY_USERNAME)),
					password = trimToOption(config.getString(PROXY_PASSWORD)),
					port = zeroToOption(config.getInt(PROXY_PORT)),
					sslPort = zeroToOption(config.getInt(PROXY_SSL_PORT)))),
			simulation = SimulationConfiguration(
				encoding = config.getString(ENCODING),
				outputFolder = getOutputFolder(config.getString(SIMULATION_OUTPUT_FOLDER)),
				requestBodiesFolder = getRequestBodiesFolder,
				pkg = config.getString(SIMULATION_PACKAGE),
				className = config.getString(SIMULATION_CLASS_NAME)),
			config)
	}
}

case class FiltersConfiguration(
	filterStrategy: FilterStrategy,
	patterns: List[Pattern])

case class HttpConfiguration(
	automaticReferer: Boolean,
	followRedirect: Boolean)

case class OutgoingProxyConfiguration(
	host: Option[String],
	username: Option[String],
	password: Option[String],
	port: Option[Int],
	sslPort: Option[Int])

case class ProxyConfiguration(
	port: Int,
	sslPort: Int,
	outgoing : OutgoingProxyConfiguration)

case class SimulationConfiguration(
	encoding: String,
	outputFolder: String,
	requestBodiesFolder: String,
	pkg: String,
	className: String)

case class RecorderConfiguration(
	filters: FiltersConfiguration,
	http: HttpConfiguration,
	proxy: ProxyConfiguration,
	simulation: SimulationConfiguration,
	config: Config)