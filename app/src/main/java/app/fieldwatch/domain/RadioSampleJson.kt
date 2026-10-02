package app.fieldwatch.domain

import org.json.JSONArray
import org.json.JSONObject

/** Full available radio sample, shared by sit exports and rotating JSONL logs. */
object RadioSampleJson {
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
