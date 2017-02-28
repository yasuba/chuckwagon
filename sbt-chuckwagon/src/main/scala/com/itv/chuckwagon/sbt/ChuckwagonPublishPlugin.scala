package com.itv.chuckwagon.sbt

import com.itv.chuckwagon.deploy.AWSCompiler
import com.itv.chuckwagon.sbt.ChuckwagonBasePlugin.autoImport._
import sbt.Keys.streams
import sbt.AutoPlugin
import LoggingUtils._
import com.itv.aws.events.ScheduleExpression
import fansi.Color.Green
import fansi.Str
import sbt.complete.DefaultParsers.{StringBasic, token}
import Parsers._

object ChuckwagonPublishPlugin extends AutoPlugin {

  override def requires = sbtassembly.AssemblyPlugin && com.itv.chuckwagon.sbt.ChuckwagonBasePlugin

  object autoImport extends Keys.Publish
  import autoImport._


  override lazy val projectSettings =
    Seq(
      chuckEnvironments := chuckDefineEnvironments("blue-qa", "qa"),
      chuckVpnConfigDeclaration := None,
      chuckSDKFreeCompiler := new AWSCompiler(
        com.itv.aws.lambda.awsLambda(chuckLambdaRegion.value)
      ),
      chuckCurrentAliases := {
        val maybeAliases = com.itv.chuckwagon.deploy
          .listAliases(chuckLambdaDeclaration.value.name)
          .foldMap(chuckSDKFreeCompiler.value.compiler)

        maybeAliases match {
          case Some(aliases) => {
            streams.value.log.info(
              logItemsMessage(
                "Current Aliases and associated Lambda Versions",
                aliases.map(
                  alias => s"${alias.name.value}=${alias.lambdaVersion.value}"
                ): _*
              )
            )
          }
          case None =>
            streams.value.log
              .info(logItemsMessage("No Lambda defined so no aliases exist"))
        }
        maybeAliases
      },
      chuckCurrentPublishedLambdas := {
        val maybePublishedLambdas = com.itv.chuckwagon.deploy
          .listPublishedLambdasWithName(chuckLambdaDeclaration.value.name)
          .foldMap(chuckSDKFreeCompiler.value.compiler)

        maybePublishedLambdas match {
          case Some(publishedLambdas) => {
            streams.value.log.info(
              logItemsMessage(
                "Currently published versions",
                publishedLambdas.map(_.version.value.toString): _*
              )
            )
          }
          case None =>
            streams.value.log.info(
              logItemsMessage(
                "No Lambda defined so no published versions exist"
              )
            )
        }

        maybePublishedLambdas
      },
      chuckPublish := {
        val maybeVpcConfig = chuckVpcConfig.value
        val lambdaDeclaration = maybeVpcConfig match {
          case Some(vpcConfig) => chuckLambdaDeclaration.value.copy(vpcConfig = Option(vpcConfig))
          case None => chuckLambdaDeclaration.value
        }

        val publishedLambda =
          com.itv.chuckwagon.deploy
            .publishLambda(
              lambdaDeclaration,
              chuckStagingS3Address.value,
              sbtassembly.AssemblyKeys.assembly.value
            )
            .foldMap(chuckSDKFreeCompiler.value.compiler)

        val alias =
          com.itv.chuckwagon.deploy
            .aliasPublishedLambda(
              publishedLambda,
              chuckEnvironments.value.head.aliasName
            )
            .foldMap(chuckSDKFreeCompiler.value.compiler)

        streams.value.log.info(
          logMessage(
            (Str("Just Published Version '") ++ Green(
              publishedLambda.version.value.toString
            ) ++ Str("' to Environment '") ++ Green(alias.name.value) ++ Str(
              "' as '"
            ) ++ Green(alias.arn.value) ++ Str("'")).render
          )
        )

        ()
      },
      chuckPromote := {
        val (fromAliasName, toAliasName) = (environmentArgParser.value ~ environmentArgParser.value).parsed
        val promotedToAlias = com.itv.chuckwagon.deploy
          .promoteLambda(
            chuckLambdaDeclaration.value.name,
            fromAliasName,
            toAliasName
          )
          .foldMap(chuckSDKFreeCompiler.value.compiler)

        streams.value.log.info(
          logMessage(
            (Str("Just Promoted Version '") ++ Green(
              promotedToAlias.lambdaVersion.value.toString
            ) ++ Str("' from Environment '") ++ Green(fromAliasName.value) ++ Str(
              "' to Environment '"
            ) ++ Green(toAliasName.value) ++ Str("' as '") ++ Green(
              promotedToAlias.arn.value
            ) ++ Str("'")).render
          )
        )
        ()
      },
      chuckSetLambdaTrigger := {
        val (targetAliasName, scheduleExpressionString) =
          (environmentArgParser.value ~ (token(' ') ~> token(StringBasic))).parsed

        val maybeAliases = com.itv.chuckwagon.deploy
          .listAliases(chuckLambdaDeclaration.value.name)
          .foldMap(chuckSDKFreeCompiler.value.compiler)

        maybeAliases.getOrElse(Nil).find(alias => alias.name == targetAliasName) match {
          case Some(alias) => {
            val _ = com.itv.chuckwagon.deploy
              .setLambdaTrigger(alias, ScheduleExpression(scheduleExpressionString))
              .foldMap(chuckSDKFreeCompiler.value.compiler)

            streams.value.log.info(
              logMessage(
                (Str("Just Created Schedule Trigger for Environment '") ++ Green(alias.name.value) ++ Str("' with Expression '") ++ Green(scheduleExpressionString) ++ Str("'")).render
              )
            )
          }
          case None =>
            throw new Exception(s"Cannot set Lambda Trigger on '${chuckLambdaDeclaration.value.name.value}' because '${targetAliasName.value}' does not exist yet.")
        }
        ()
      },
      chuckCleanUp := {
        val deletedAliases =
          com.itv.chuckwagon.deploy
            .deleteRedundantAliases(
              chuckLambdaDeclaration.value.name,
              chuckEnvironments.value.toList.map(_.aliasName)
            )
            .foldMap(chuckSDKFreeCompiler.value.compiler)

        streams.value.log.info(
          logItemsMessage(
            "Deleted the following redundant aliases",
            deletedAliases.map(_.value): _*
          )
        )

        val deletedLambdaVersions =
          com.itv.chuckwagon.deploy
            .deleteRedundantPublishedLambdas(chuckLambdaDeclaration.value.name)
            .foldMap(chuckSDKFreeCompiler.value.compiler)

        streams.value.log.info(
          logItemsMessage(
            "Deleted the following redundant lambda versions",
            deletedLambdaVersions.map(_.value.toString): _*
          )
        )
        ()
      }
    )
}