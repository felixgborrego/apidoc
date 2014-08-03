package core.generator

import core._
import org.scalatest.{ ShouldMatchers, FunSpec }

class Play2JsonSpec extends FunSpec with ShouldMatchers {

  private lazy val service = TestHelper.parseFile("reference-api/api.json").serviceDescription.get

  describe("readers") {

    it("user") {
      val user = service.models.find(_.name == "user").get
      
      TestHelper.assertEqualsFile(
        "core/src/test/resources/generators/play-2-json-spec-readers-user.scala",
        Play2Json.readers(user)
      )
    }
  }

}