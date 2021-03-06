package com.lucidchart.open.nark.models

import AnormImplicits._
import anorm._
import anorm.SqlParser._
import play.api.Play.current
import play.api.db.DB

class MutexModelLockTakenException(name: String) extends Exception(name)

class MutexModel extends AppModel {
	private val lockParser = scalar[Long].singleOpt

	/**
	 * Lock a mutex, and call the callback
	 *
	 * If the mutex cannot be locked within timeout, an exception will be thrown
	 */
	def lock[A](name: String, timeoutSeconds: Int)(callback: => A): A = {
		lock[Option[A]](name, timeoutSeconds, None) {
			Some(callback)
		} match {
			case Some(x) => x
			case None => throw new MutexModelLockTakenException(name)
		}
	}

	/**
	 * Lock a mutex, and call the callback
	 *
	 * If the mutex cannot be locked within timeout, noLockReturn will be returned
	 */
	def lock[A](name: String, timeoutSeconds: Int, noLockReturn: => A)(callback: => A): A = {
		DB.withConnection("main") { connection =>
			val locked = SQL("""
				SELECT GET_LOCK({name}, {timeout})
			""").on(
				"name"    -> name,
				"timeout" -> timeoutSeconds
			).as(lockParser)(connection) match {
				case Some(x) if (x == 1) => true
				case _ => false
			}

			if (locked) {
				try {
					callback
				}
				finally {
					SQL("SELECT RELEASE_LOCK({lock})").on('lock -> name).as(lockParser)(connection)
				}
			}
			else {
				noLockReturn
			}
		}
	}
}

object MutexModel extends MutexModel
