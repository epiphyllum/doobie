package doobie.refined

import org.specs2.mutable.Specification
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined._
import doobie.imports._
import doobie.util.invariant._

#+cats
import fs2.interop.cats._
#-cats

object refinedtypes extends Specification {

  val xa = DriverManagerTransactor[IOLite](
    "org.h2.Driver",
    "jdbc:h2:mem:refined;DB_CLOSE_DELAY=-1",
    "sa", ""
  )

  type PositiveInt = Int Refined Positive

  "Atom" should {
    "exist for refined types" in {
      Atom[PositiveInt]

      true
    }
    "exist for Option of a refined type" in {
      Atom[Option[PositiveInt]]

      true
    }
  }

  case class Point(x: Int, y: Int)
  case class Quadrant1()
  type PointInQuadrant1 = Point Refined Quadrant1

  implicit val PointComposite: Composite[Point] =
    Composite[(Int, Int)]
#+scalaz
      .xmap(
        (t: (Int,Int)) => new Point(t._1, t._2),
        (p: Point) => (p.x, p.y)
      )
#-scalaz
#+cats
      .imap((t: (Int,Int)) => new Point(t._1, t._2))((p: Point) => (p.x, p.y))
#-cats

  implicit val quadrant1Validate: Validate.Plain[Point, Quadrant1] =
    Validate.fromPredicate(p => p.x >= 0 && p.y >= 0, p => s"($p is in quadrant 1)", Quadrant1())

  "Composite" should {
    "exist for refined types" in {
      Composite[PointInQuadrant1]

      true
    }
  }

  "Query" should {
    "return a refined type when conversion is possible" in {
      sql"select 123".query[PositiveInt].unique.transact(xa).unsafePerformIO

      true
    }

    "return an Option of a refined type when query returns null-value" in {
      sql"select NULL".query[Option[PositiveInt]].unique.transact(xa).unsafePerformIO

      true
    }

    "return an Option of a refined type when query returns a value and converion is possible" in {
      sql"select NULL".query[Option[PositiveInt]].unique.transact(xa).unsafePerformIO

      true
    }

    "save a None of a refined type" in {
      val none: Option[PositiveInt] = None
      insertOptionalPositiveInt(none)

      true
    }

    "save a Some of a refined type" in {
      val somePositiveInt: Option[PositiveInt] = refineV[Positive](5).right.toOption
      insertOptionalPositiveInt(somePositiveInt)

      true
    }

    def insertOptionalPositiveInt(v: Option[PositiveInt]) = {
      val queryRes = for {
        _  <- Update0(s"CREATE LOCAL TEMPORARY TABLE TEST (value INT)", None).run
        _  <- sql"INSERT INTO TEST VALUES ($v)".update.run
      } yield ()

      queryRes.transact(xa).unsafePerformIO
    }

    "throw an SecondaryValidationFailed if value does not fit the refinement-type " in {
      secondaryValidationFailedCaught_?(
       sql"select -1".query[PositiveInt].unique.transact(xa).unsafePerformIO
      )
    }

    "return a refined product-type when conversion is possible" in {
      sql"select 1, 1".query[PointInQuadrant1].unique.transact(xa).unsafePerformIO

      true
    }

    "throw an SecondaryValidationFailed if object does not fit the refinement-type " in {
      secondaryValidationFailedCaught_?(
        sql"select -1, 1".query[PointInQuadrant1].unique.transact(xa).unsafePerformIO
      )
    }
  }

  private[this] def secondaryValidationFailedCaught_?(query: => Unit): Boolean = try {
      query
      false
    }
    catch {
      case e: SecondaryValidationFailed[_] => true
      case _: Throwable => false
    }

}
