package com.lucidchart.open.nark.models

import anorm._
import anorm.SqlParser._
import AnormImplicits._
import com.lucidchart.open.nark.models.records.Alert
import com.lucidchart.open.nark.models.records.AlertState
import com.lucidchart.open.nark.models.records.AlertTargetState
import java.util.{Date, UUID}
import play.api.Play.current
import play.api.Play.configuration
import play.api.db.DB

object AlertTargetStateModel extends AlertTargetStateModel
class AlertTargetStateModel extends AppModel {
	protected val AlertTargetStateRowParser = {
		get[UUID]("alert_id") ~ 
		get[String]("target") ~
		get[Int]("state") ~
		get[Date]("last_updated") map {
			case alert_id ~ target ~ state ~ last_updated =>
				new AlertTargetState(alert_id, target, AlertState(state), last_updated)
		}
	}

	/**
	 * updates ALL existing target states for the alert
	 */
	def setAlertTargetStates(alert: Alert, states: List[AlertTargetState]) {
		DB.withTransaction("main") { connection =>
			if(!states.isEmpty) {
				states.groupBy(_.state).map { case (state, records) =>
					RichSQL("""
						INSERT INTO `alert_target_state` (`alert_id`, `target`,`state`,`last_updated`) 
						VALUES ({fields}) ON DUPLICATE KEY UPDATE `state` = {update_state}, `last_updated` = VALUES(`last_updated`)
					""").multiInsert(records.size, Seq("alert_id", "target", "state", "last_updated"))(
						"alert_id"      -> records.map(s => toParameterValue(s.alertId)),
						"target"        -> records.map(s => toParameterValue(s.target)),
						"state"         -> records.map(s => toParameterValue(s.state.id)),
						"last_updated"  -> records.map(s => toParameterValue(s.lastUpdated)))
					.toSQL.on(
						"update_state" -> state.id
					).executeUpdate()(connection)
				}
			}

			//delete all old targets of this alert that have not been updated in 100 intervals
			val now = new Date
			val hundredIntervals = new Date(now.getTime - (100000 * alert.frequency))
			SQL("""
				DELETE FROM `alert_target_state`
				WHERE `alert_id` = {alert_id} AND `last_updated` < {limit}
			""").on(
				"alert_id" -> alert.id,
				"limit" -> hundredIntervals
			).executeUpdate()(connection)
		}
	}

	/**
	 * Get all target states for the alert
	 * @param alertId the alert to get the target states for.
	 */
	def getTargetStatesByAlertID(alertId: UUID): List[AlertTargetState] = {
		DB.withConnection("main") { connection =>
			SQL("""
				SELECT * 
				FROM `alert_target_state`
				WHERE `alert_id` = {alert_id}
			""").on(
				"alert_id" -> alertId
			).as(AlertTargetStateRowParser * )(connection)
		}
	}

	/**
	 * Get paginated results of sick targets
	 * @param alertIds the alerts for which to get sick targets
	 * @param page the page to get
	 * @return a page of sick targets
	 */
	def getSickTargets(page: Int): List[AlertTargetState] = {
		DB.withConnection("main") { connection =>
			SQL("""
				SELECT *
				FROM `alert_target_state`
				WHERE `state` IN ({error}, {warn})
				ORDER BY `alert_id` ASC
				LIMIT {limit} OFFSET {offset}
			""").on(
				"error" -> AlertState.error.id,
				"warn" -> AlertState.warn.id,
				"limit" -> configuredLimit,
				"offset" -> configuredLimit * page
			).as(AlertTargetStateRowParser *)(connection)
		}	
	}

	/**
	 * Get all sick targets for the alerts
	 * @param alertIds the alerts for which to get sick targets
	 * @return a list of sick targets
	 */
	def getSickTargets(alertIds: List[UUID]): List[AlertTargetState] = {
		if (alertIds.isEmpty){
			Nil
		}
		else {
			DB.withConnection("main") { connection =>
				RichSQL("""
					SELECT *
					FROM `alert_target_state`
					WHERE `alert_id` IN ({ids}) AND `state` IN ({error}, {warn})
				""").onList(
					"ids" -> alertIds
				).toSQL.on(
					"error" -> AlertState.error.id,
					"warn" -> AlertState.warn.id
				).as(AlertTargetStateRowParser *)(connection)
			}
		}
	}
}
