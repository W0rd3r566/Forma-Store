package dev.formastore.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Only verified Repository results may be written here. Never stores a network response. */
internal class CatalogCache(context: Context) : SQLiteOpenHelper(context, "catalog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE metadata (id INTEGER PRIMARY KEY, saved INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    fun load(): List<StoreApp> = readableDatabase.rawQuery("SELECT data FROM catalog ORDER BY id", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(decode(JSONObject(cursor.getString(0)))) }
    }
    fun savedAt(): Long = readableDatabase.rawQuery("SELECT saved FROM metadata WHERE id=1", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
    fun save(apps: List<StoreApp>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("catalog", null, null)
            db.compileStatement("INSERT INTO catalog(id,data) VALUES(?,?)").use { statement ->
                for (app in apps) {
                    statement.bindString(1, app.id)
                    statement.bindString(2, encode(app).toString())
                    statement.executeInsert()
                    statement.clearBindings()
                }
            }
            db.execSQL("INSERT OR REPLACE INTO metadata(id,saved) VALUES(1,?)", arrayOf(System.currentTimeMillis()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun encode(a: StoreApp): JSONObject = JSONObject().apply {
        put("id", a.id); put("name", a.name); put("summary", a.summary); put("description", a.description)
        put("license", a.license); put("source", a.source); put("icon", a.icon ?: JSONObject.NULL)
        put("categories", JSONArray(a.categories)); put("version", a.version); put("code", a.code)
        put("apk", a.apk); put("hash", a.hash); put("size", a.size); put("minSdk", a.minSdk); put("targetSdk", a.targetSdk)
        put("abis", JSONArray(a.abis)); put("permissions", JSONArray(a.permissions)); put("antiFeatures", JSONArray(a.antiFeatures))
    }
    private fun decode(j: JSONObject): StoreApp {
        fun list(key: String): List<String> { val a = j.getJSONArray(key); return (0 until a.length()).map { a.getString(it) } }
        return StoreApp(j.getString("id"), j.getString("name"), j.getString("summary"), j.getString("description"),
            j.getString("license"), j.getString("source"), if (j.isNull("icon")) null else j.getString("icon"), list("categories"),
            j.getString("version"), j.getLong("code"), j.getString("apk"), j.getString("hash"), j.getLong("size"),
            j.getInt("minSdk"), j.getInt("targetSdk"), list("abis"), list("permissions"), list("antiFeatures"))
    }
}
