package com.social_downloader.data

import org.json.JSONObject
import java.io.InputStream

object ProfileBackup {
    data class Profile(val experience: Experience, val theme: ThemeMode, val dynamic: Boolean)
    fun encode(profile: Profile) = JSONObject().put("kind","AnyDown customization").put("schema",1)
        .put("appearance",JSONObject(profile.experience.json())).put("theme",profile.theme.name).put("dynamic",profile.dynamic).toString(2)
    fun read(input: InputStream): Profile {
        val data=ByteArray(16385); var count=0
        while(count<data.size) { val n=input.read(data,count,data.size-count); if(n<0)break; if(n==0)continue; count+=n }
        require(count<=16384){"Settings file is too large"}
        val j=JSONObject(String(data,0,count,Charsets.UTF_8))
        require(j.optString("kind")=="AnyDown customization" && j.optInt("schema")==1){"Not a supported AnyDown settings file"}
        return Profile(Experience.parse(j.getJSONObject("appearance").toString()),
            runCatching { ThemeMode.valueOf(j.getString("theme")) }.getOrDefault(ThemeMode.SYSTEM),j.optBoolean("dynamic",true))
    }
}
