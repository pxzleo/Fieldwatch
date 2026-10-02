package app.fieldwatch.domain

import org.json.JSONArray
import org.json.JSONObject

/** Full available radio sample, shared by sit exports and rotating JSONL logs. */
object RadioSampleJson {
    fun readFacts(sample: JSONObject): RadioFacts {
        if (!sample.has("facts") || sample.isNull("facts")) return RadioFacts()
        val obj = sample.getJSONObject("facts")
        fun integer(record: JSONObject, key: String, path: String): Int? {
            if (!record.has(key) || record.isNull(key)) return null
            return record.get(key).toString().toIntOrNull()
                ?: throw org.json.JSONException("$path.$key must be an integer")
        }
        fun int(key: String): Int? = integer(obj, key, "facts")
        fun number(key: String): Double? {
            if (!obj.has(key) || obj.isNull(key)) return null
            return obj.get(key).toString().toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw org.json.JSONException("facts.$key must be a finite number")
        }
        fun text(key: String): String? = if (obj.has(key) && !obj.isNull(key)) obj.getString(key).ifBlank { null } else null
        fun records(key: String): List<JSONObject> {
            if (!obj.has(key) || obj.isNull(key)) return emptyList()
            val array = obj.getJSONArray(key)
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
        return RadioFacts(
            txPowerDbm = int("tx_power_dbm"), advFlags = int("adv_flags"), appearance = int("appearance"),
            addressType = text("address_type"), advertisingIntervalMs = number("advertising_interval_ms"),
            periodicIntervalMs = number("periodic_interval_ms"),
            connectable = if (obj.has("connectable") && !obj.isNull("connectable"))
                obj.get("connectable") as? Boolean ?: throw org.json.JSONException("facts.connectable must be a boolean") else null,
            primaryPhy = text("primary_phy"), secondaryPhy = text("secondary_phy"), deviceClass = int("device_class"),
            wifiStandard = text("wifi_standard"), channelWidth = text("channel_width"),
            centerFreq0 = int("center_freq0"), centerFreq1 = int("center_freq1"), capabilities = text("capabilities"),
            supportedRates = text("supported_rates"), security = text("security"),
            mfgRecords = records("mfg_records").map {
                MfgRecord(integer(it, "company_id", "facts.mfg_records")
                    ?: throw org.json.JSONException("facts.mfg_records.company_id is required"), it.optString("data_hex"))
            },
            vendorIes = records("vendor_ies").map {
                VendorIeRecord(it.optString("oui"), integer(it, "type", "facts.vendor_ies") ?: -1, it.optString("data_hex"))
            },
            serviceData = records("service_data").map { ServiceDataRecord(it.optString("uuid"), it.optString("data_hex")) },
        )
    }

    fun appendTo(target: JSONObject, device: Sighting): JSONObject {
        val facts = device.facts
        val detail = JSONObject()
            .put("tx_power_dbm", facts.txPowerDbm ?: JSONObject.NULL)
            .put("adv_flags", facts.advFlags ?: JSONObject.NULL)
            .put("appearance", facts.appearance ?: JSONObject.NULL)
            .put("address_type", facts.addressType ?: JSONObject.NULL)
            .put("advertising_interval_ms", facts.advertisingIntervalMs ?: JSONObject.NULL)
            .put("periodic_interval_ms", facts.periodicIntervalMs ?: JSONObject.NULL)
            .put("connectable", facts.connectable ?: JSONObject.NULL)
            .put("primary_phy", facts.primaryPhy ?: JSONObject.NULL)
            .put("secondary_phy", facts.secondaryPhy ?: JSONObject.NULL)
            .put("device_class", facts.deviceClass ?: JSONObject.NULL)
            .put("wifi_standard", facts.wifiStandard ?: JSONObject.NULL)
            .put("channel_width", facts.channelWidth ?: JSONObject.NULL)
            .put("center_freq0", facts.centerFreq0 ?: JSONObject.NULL)
            .put("center_freq1", facts.centerFreq1 ?: JSONObject.NULL)
            .put("capabilities", facts.capabilities ?: JSONObject.NULL)
            .put("supported_rates", facts.supportedRates ?: JSONObject.NULL)
            .put("security", facts.security ?: JSONObject.NULL)
            .put("mfg_records", JSONArray(facts.mfgRecords.map {
                JSONObject().put("company_id", it.companyId).put("data_hex", it.dataHex)
            }))
            .put("service_data", JSONArray(facts.serviceData.map {
                JSONObject().put("uuid", it.uuid).put("data_hex", it.dataHex)
            }))
            .put("vendor_ies", JSONArray(facts.vendorIes.map {
                JSONObject().put("oui", it.oui).put("type", it.type).put("data_hex", it.dataHex)
            }))
        return target
            .put("raw_hex", device.rawHex)
            .put("manufacturer_id", device.manufacturerId ?: JSONObject.NULL)
            .put("manufacturer_data_hex", device.manufacturerDataHex)
            .put("service_uuids", JSONArray(device.serviceUuids))
            .put("vendor_ie_ouis", JSONArray(device.vendorIeOuis))
            .put("extras", device.extras)
            .put("facts", detail)
    }
}
