package com.mparticle.kits

import android.content.Context
import com.mparticle.kits.KitIntegration.CommerceListener
import com.mparticle.kits.KitIntegration.AttributeListener
import org.json.JSONObject
import com.mparticle.MParticle
import com.crittercism.app.Crittercism
import com.crittercism.app.CrittercismConfig
import java.util.LinkedList
import com.mparticle.commerce.CommerceEvent
import com.mparticle.commerce.Product
import com.mparticle.MPEvent
import com.mparticle.MParticle.IdentityType
import com.mparticle.internal.Logger
import org.json.JSONException
import java.lang.Exception
import java.math.BigDecimal
import java.net.URL
import java.net.MalformedURLException

/**
 * Crittercism Kit for version 5.5.x of the Crittercism SDK.
 */
class CrittercismKit : KitIntegration(), CommerceListener, KitIntegration.EventListener,
    AttributeListener {
    private var mUserAttributes: JSONObject? = null
    override fun getName(): String = KIT_NAME

    override fun onKitCreate(
        settings: Map<String, String>,
        context: Context
    ): List<ReportingMessage> {
        onCreate()
        return emptyList()
    }

    /**
     * Crittercism checks the stack trace for "onCreate" || "onResume" and will print a warning if not found.
     *
     * This method is a hack to prevent that warning from showing up in logcat.
     */
    private fun onCreate() {
        if (mUserAttributes == null) {
            mUserAttributes = JSONObject()
        }
        if (MParticle.getInstance()?.environment == MParticle.Environment.Development) {
            Crittercism.setLoggingLevel(Crittercism.LoggingLevel.Info)
        }
        val config = CrittercismConfig()
        config.isServiceMonitoringEnabled = (settings[SERVICE_MONITORING].toBoolean())
        Crittercism.initialize(context, settings[APP_ID], config)
    }

    override fun leaveBreadcrumb(breadcrumb: String): List<ReportingMessage> {
        Crittercism.leaveBreadcrumb(breadcrumb)
        val messages = LinkedList<ReportingMessage>()
        messages.add(
            ReportingMessage(
                this,
                ReportingMessage.MessageType.BREADCRUMB,
                System.currentTimeMillis(),
                null
            )
        )
        return messages
    }

    override fun logError(
        message: String,
        errorAttributes: Map<String, String>
    ): List<ReportingMessage> = emptyList()

    override fun logLtvIncrease(
        valueIncreased: BigDecimal,
        valueTotal: BigDecimal,
        eventName: String,
        contextInfo: Map<String, String>
    ): List<ReportingMessage> = emptyList()

    override fun logEvent(event: CommerceEvent): List<ReportingMessage> {
        if (event.productAction == Product.PURCHASE || event.productAction == Product.REFUND) {
            Crittercism.beginTransaction(event.productAction)
            event.transactionAttributes
                ?.revenue?.times(100)?.let {
                    Crittercism.setTransactionValue(
                        event.productAction, it.toInt()
                    )
                }
            if (event.productAction == Product.REFUND) {
                Crittercism.failTransaction(event.productAction)
            } else {
                Crittercism.endTransaction(event.productAction)
            }
        } else {
            val eventList = CommerceEventUtils.expand(event)
            for (mpEvent in eventList) {
                logEvent(mpEvent)
            }
        }
        val messages = LinkedList<ReportingMessage>()
        messages.add(ReportingMessage.fromEvent(this, event))
        return messages
    }

    override fun setUserIdentity(identityType: IdentityType, identity: String) {
        if (IdentityType.CustomerId == identityType) {
            Crittercism.setUsername(identity)
        }
    }

    override fun removeUserIdentity(identityType: IdentityType) {
        if (IdentityType.CustomerId == identityType) {
            Crittercism.setUsername("")
        }
    }

    override fun logout(): List<ReportingMessage> = emptyList()

    override fun setUserAttribute(key: String, value: String) {
        try {
            mUserAttributes?.put(KitUtils.sanitizeAttributeKey(key), value)
        } catch (e: JSONException) {
        }
        Crittercism.setMetadata(mUserAttributes)
    }

    override fun setUserAttributeList(key: String, list: List<String>) {}
    override fun supportsAttributeLists(): Boolean = false

    override fun setAllUserAttributes(
        userAttributes: Map<String, String>,
        userAttributeList: Map<String, List<String>>
    ) {
        mUserAttributes = mUserAttributes ?: JSONObject()
        mUserAttributes?.let {
            userAttributes.iterator()?.forEach { (key, value) ->
                try {
                    it.put(key, value)
                } catch (_: JSONException) {
                }
            }
        }
        Crittercism.setMetadata(mUserAttributes)
    }

    override fun removeUserAttribute(key: String) {
        mUserAttributes?.remove(KitUtils.sanitizeAttributeKey(key))
        Crittercism.setMetadata(mUserAttributes)
    }

    override fun setOptOut(optOutStatus: Boolean): List<ReportingMessage> {
        Crittercism.setOptOutStatus(optOutStatus)
        return listOf(
            ReportingMessage(
                this,
                ReportingMessage.MessageType.OPT_OUT,
                System.currentTimeMillis(),
                null
            )
        )
    }

    override fun logEvent(event: MPEvent): List<ReportingMessage> {
        Crittercism.leaveBreadcrumb(event.eventName)
        return listOf(ReportingMessage.fromEvent(this, event))
    }

    override fun logScreen(
        screenName: String,
        eventAttributes: Map<String, String>
    ): List<ReportingMessage> {
        Crittercism.leaveBreadcrumb(screenName)
        return listOf(
            ReportingMessage(
                this,
                ReportingMessage.MessageType.SCREEN_VIEW,
                System.currentTimeMillis(),
                null
            ).setScreenName(screenName)
        )
    }

    override fun logException(
        exception: Exception,
        eventData: Map<String, String>,
        message: String
    ): List<ReportingMessage> {
        Crittercism.logHandledException(exception)
        val reportingMessage = ReportingMessage(
            this,
            ReportingMessage.MessageType.ERROR,
            System.currentTimeMillis(),
            eventData
        )
        reportingMessage.setExceptionClassName(exception.javaClass.canonicalName)
        return listOf(reportingMessage)
    }

    public override fun logNetworkPerformance(
        url: String,
        startTime: Long,
        method: String,
        length: Long,
        bytesSent: Long,
        bytesReceived: Long,
        requestString: String,
        responseCode: Int
    ): List<ReportingMessage> {
        var critUrl: URL? = null
        try {
            critUrl = URL(url)
        } catch (e: MalformedURLException) {
            Logger.error("Invalid URL sent to logNetworkPerformance: $url")
        }
        Crittercism.logNetworkRequest(
            method,
            critUrl,
            length,
            bytesReceived,
            bytesSent,
            responseCode,
            null
        )
        return listOf(
            ReportingMessage(
                this,
                ReportingMessage.MessageType.NETWORK_PERFORMNACE,
                System.currentTimeMillis(),
                null
            )
        )
    }

    companion object {
        private const val APP_ID = "appid"
        private const val SERVICE_MONITORING = "service_monitoring_enabled"
        const val KIT_NAME = "Apteligent"
    }
}