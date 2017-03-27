package com.itv.chuckwagon.sbt.builder

import com.itv.aws.ec2.Filter
import com.itv.aws.lambda.VpcConfigDeclaration

import scala.language.implicitConversions

object VpcConfigDeclarationBuilder {
  implicit def enableVpcConfigDeclarationBuilder(
      builder: VpcConfigDeclarationBuilder[DEFINED, DEFINED, DEFINED]
  ): VpcConfigDeclaration =
    VpcConfigDeclaration(
      builder.vpcLookupFilters.get,
      builder.subnetsLookupFilters.get,
      builder.securityGroupsLookupFilters.get
    )

  abstract class UNDEFINED_VpcLookupFilters
  abstract class UNDEFINED_SubnetsLookupFilters
  abstract class UNDEFINED_SecurityGroupsLookupFilters

  def apply() =
    new VpcConfigDeclarationBuilder[
      UNDEFINED_VpcLookupFilters,
      UNDEFINED_SubnetsLookupFilters,
      UNDEFINED_SecurityGroupsLookupFilters
    ](None, None, None)
}

class VpcConfigDeclarationBuilder[B_VPC_LOOKUP_FILTERS,
                                  B_SUBNETS_LOOKUP_FILTERS,
                                  B_SECURITYGROUPS_LOOKUP_FILTERS](
    val vpcLookupFilters: Option[List[Filter]],
    val subnetsLookupFilters: Option[List[Filter]],
    val securityGroupsLookupFilters: Option[List[Filter]]
) {
  def withVPCLookupFilters(vpcLookupFilters: (String, String)*) =
    new VpcConfigDeclarationBuilder[DEFINED, B_SUBNETS_LOOKUP_FILTERS, B_SECURITYGROUPS_LOOKUP_FILTERS](
      toFilters(vpcLookupFilters),
      subnetsLookupFilters,
      securityGroupsLookupFilters
    )

  def withSubnetsLookupFilters(subnetsLookupFilters: (String, String)*) =
    new VpcConfigDeclarationBuilder[B_VPC_LOOKUP_FILTERS, DEFINED, B_SECURITYGROUPS_LOOKUP_FILTERS](
      vpcLookupFilters,
      toFilters(subnetsLookupFilters),
      securityGroupsLookupFilters
    )

  def withSecurityGroupsLookupFilters(securityGroupsLookupFilters: (String, String)*) =
    new VpcConfigDeclarationBuilder[B_VPC_LOOKUP_FILTERS, B_SUBNETS_LOOKUP_FILTERS, DEFINED](
      vpcLookupFilters,
      subnetsLookupFilters,
      toFilters(securityGroupsLookupFilters)
    )

  private def toFilters(stringFilters: Seq[(String, String)]): Option[List[Filter]] =
    Option(stringFilters.toList.map(t => Filter(t._1, t._2)))
}