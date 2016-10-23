package demo

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.{Member, Cluster, MemberStatus}

import scala.collection.immutable.SortedSet

object ClusterMembershipSupport {
  def props(cluster: Cluster) = Props(new ClusterMembershipSupport(cluster))
}

class ClusterMembershipSupport(cluster: Cluster) extends Actor with ActorLogging {

  override def preStart = {
    cluster.subscribe(self, classOf[ClusterDomainEvent])
  }

  private def evolve(clusterMembers: SortedSet[Member]): Receive = {
    case MemberUp(member) =>
      log.info("MemberUp = {}", member.address)
      context become (evolve(clusterMembers + member))

    case MemberExited(member) =>
      log.info("MemberExited = {}", member.address)

    case ReachableMember(member) =>
      log.info("ReachableMember = {}", member.address)

    case UnreachableMember(member) =>
      log.info("UnreachableMember = {}", member.address)

    case MemberRemoved(member, prev) =>
      if (prev == MemberStatus.Exiting) log.debug("{} gracefully exited", member.address)
      else log.info("{} downed after Unreachable", member.address)
      context become evolve(clusterMembers - member)

    case state: CurrentClusterState =>
      log.info("Cluster state = {}", state.members)
      context become evolve(state.members)

    case 'Members =>
      sender() ! clusterMembers.mkString(",")
  }

  override def receive = evolve(SortedSet[Member]())
}