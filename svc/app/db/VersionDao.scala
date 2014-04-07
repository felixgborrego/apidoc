package db

import core.{ Service, User }
import lib.{ Constants, VersionSortKey }
import anorm._
import play.api.db._
import play.api.libs.json._
import play.api.Play.current
import java.util.UUID

case class Version(guid: String, version: String, json: String) {

  def softDelete(deletedBy: User) {
    DB.withConnection { implicit c =>
      SQL("""
          update versions set deleted_by_guid = {deleted_by_guid}::uuid, deleted_at = now() where guid = {guid}::uuid and deleted_at is null
          """).on('deleted_by_guid -> deletedBy.guid, 'guid -> guid).execute()
    }
  }

  def replace(user: User, service: Service, newJson: String): Version = {
    DB.withTransaction { implicit c =>
      softDelete(user)
      VersionDao.create(user, service, version, newJson)
    }
  }

}

object Version {

  implicit val versionReads = Json.reads[Version]
  implicit val versionWrites = Json.writes[Version]

}

case class VersionQuery(service_guid: String,
                        guid: Option[String] = None,
                        version: Option[String] = None,
                        limit: Int = 50,
                        offset: Int = 0)

object VersionDao {

  implicit val versionReads = Json.reads[Version]
  implicit val versionWrites = Json.writes[Version]

  private val BaseQuery = """
    select guid::varchar, version, json::varchar
     from versions
    where deleted_at is null
  """

  def create(user: User, service: Service, version: String, json: String): Version = {
    val v = Version(guid = UUID.randomUUID.toString,
                    version = version,
                    json = json)

    DB.withConnection { implicit c =>
      SQL("""
          insert into versions
          (guid, service_guid, version, version_sort_key, json, created_by_guid)
          values
          ({guid}::uuid, {service_guid}::uuid, {version}, {version_sort_key}, {json}::json, {created_by_guid}::uuid)
          """).on('guid -> v.guid,
                  'service_guid -> service.guid,
                  'version -> v.version,
                  'version_sort_key -> VersionSortKey.generate(v.version),
                  'json -> v.json,
                  'created_by_guid -> user.guid).execute()
    }

    v
  }

  def findByServiceAndVersion(service: Service, version: String): Option[Version] = {
    VersionDao.findAll(VersionQuery(service_guid = service.guid,
                                    version = Some(version),
                                    limit = 1)).headOption
  }

  def findAll(query: VersionQuery): Seq[Version] = {
    val sql = Seq(
      Some(BaseQuery.trim),
      query.guid.map { v => "and versions.guid = {guid}::uuid" },
      Some("and versions.service_guid = {service_guid}::uuid"),
      query.version.map { v => "and versions.version = {version}" },
      Some(s"order by versions.version_sort_key desc, versions.created_at desc limit ${query.limit} offset ${query.offset}")
    ).flatten.mkString("\n   ")

    val bind = Seq(
      query.guid.map { v => 'guid -> toParameterValue(v) },
      Some('service_guid -> toParameterValue(query.service_guid)),
      query.version.map { v => 'version -> toParameterValue(v) }
    ).flatten

    DB.withConnection { implicit c =>
      SQL(sql).on(bind: _*)().toList.map { row =>
        Version(guid = row[String]("guid"),
                version = row[String]("version"),
                json = row[String]("json"))
        }.toSeq
    }
  }

}