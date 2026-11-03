package co.yooz.hamresan

import android.app.Application
import androidx.room.Room
import co.yooz.hamresan.data.Db
import co.yooz.hamresan.data.Prefs
import co.yooz.hamresan.data.Repo
import co.yooz.hamresan.mesh.MeshState
import co.yooz.hamresan.net.ServerApi

class App : Application() {
    lateinit var db: Db
    lateinit var prefs: Prefs
    lateinit var repo: Repo
    lateinit var server: ServerApi
    val mesh = MeshState()

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        db = Room.databaseBuilder(this, Db::class.java, "hamresan.db")
            .fallbackToDestructiveMigration(dropAllTables = true)   // v1 test data only; content re-syncs from neighbours
            .build()
        repo = Repo(db, prefs)
        server = ServerApi(prefs, repo)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
