package vanillakotlin.db

import vanillakotlin.models.HealthMonitor
import com.target.health.HealthMonitor as TargetHealthMonitor

fun TargetHealthMonitor.toHealthMonitor(): HealthMonitor {
    return this as HealthMonitor
}
