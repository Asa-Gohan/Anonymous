package personal.wuqing.anonymous

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random
import kotlin.time.ExperimentalTime

object Network {
    private const val IP = "182.254.145.254"
    private const val PORT = 8080
    var token = ""
    private fun connect(data: JSONObject) = Socket().use {
        it.soTimeout = 20 * 1000
        it.connect(InetSocketAddress(IP, PORT))
        it.getOutputStream().write(data.toString().toByteArray())
        JSONObject(it.getInputStream().bufferedReader().readLine().run {
            if (endsWith('}')) this else "$this}"
        })
    }

    object NotLoggedInException : Exception()

    private fun <T> getData(
        op: String, checkLogin: Boolean = true,
        p1: String = "0", p2: String = "0", p3: String = "0", p4: String = "0", p5: String = "0",
        done: JSONObject.() -> T
    ): T {
        val json = JSONObject(
            mapOf(
                "op_code" to op,
                "pa_1" to p1,
                "pa_2" to p2,
                "pa_3" to p3,
                "pa_4" to p4,
                "pa_5" to p5,
                "Token" to token
            )
        )
        val result = connect(json)
        return if (checkLogin && result.has("login_flag") && result.getString("login_flag") != "1") throw NotLoggedInException
        else done(result)
    }

    suspend fun requestLoginCode(email: String) = withContext(Dispatchers.IO) {
        getData(op = "0", checkLogin = false, p1 = email) {
            getInt("VarifiedEmailAddress") == 1
        }
    }

    suspend fun login(email: String, code: String, device: String) = withContext(Dispatchers.IO) {
        getData(op = "f", checkLogin = false, p1 = email, p2 = code, p3 = device) {
            (getInt("login_flag") == 0).also {
                if (it) token = getString("Token")
            }
        }
    }

    suspend fun verifyToken() = withContext(Dispatchers.IO) {
        getData(op = "-1", checkLogin = false) {
            getString("login_flag") == "1"
        }
    }

    suspend fun likePost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "8_3", p1 = id) { true }
    }

    suspend fun unlikePost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "8_4", p1 = id) { true }
    }

    suspend fun favorPost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "5", p1 = id) { true }
    }

    suspend fun deFavorPost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "5_2", p1 = id) { true }
    }

    enum class PostType(val op: String) {
        TIME("1"), FAVOURED("6"), MY("7"), TRENDING("d")
    }

    @ExperimentalTime
    suspend fun fetchPost(type: PostType, category: Post.Category, last: String = "NULL") =
        withContext(Dispatchers.IO) {
            getData(op = type.op, p1 = last, p2 = category.id.toString()) {
                var lastSeen = "NULL"
                for (key in keys()) if (key.startsWith("LastSeen")) lastSeen = getString(key)
                lastSeen to getJSONArray("thread_list").let {
                    (0 until it.length()).map { i -> Post(it.getJSONObject(i), false) }
                }
            }
        }

    @ExperimentalTime
    suspend fun search(keyword: String, last: String = "NULL") = withContext(Dispatchers.IO) {
        getData(op = "b", p1 = keyword, p2 = last) {
            var lastSeen = "NULL"
            for (key in keys()) if (key.startsWith("LastSeen")) lastSeen = getString(key)
            lastSeen to getJSONArray("thread_list").let {
                (0 until it.length()).map { i -> Post(it.getJSONObject(i), false) }
            }
        }
    }

    @ExperimentalTime
    suspend fun fetchReply(postId: String, order: Boolean, last: String = "NULL") =
        withContext(Dispatchers.IO) {
            getData(op = "2", p1 = postId, p2 = last, p3 = if (order) "1" else "0") {
                val post = Post(getJSONObject("this_thread"), true)
                var newLast = "NULL"
                for (key in keys()) if (key.startsWith("LastSeen")) newLast = getString(key)
                newLast to post to getJSONArray("floor_list").let {
                    (0 until it.length()).map { i ->
                        Reply(it.getJSONObject(i), post.nameG, post.colorG)
                    }
                }
            }
        }

    suspend fun likeReply(post: String, reply: String) = withContext(Dispatchers.IO) {
        getData(op = "8", p1 = post, p4 = reply) { true }
    }

    suspend fun unlikeReply(post: String, reply: String) = withContext(Dispatchers.IO) {
        getData(op = "8_2", p1 = post, p4 = reply) { true }
    }

    suspend fun reply(post: String, reply: String, content: String) = withContext(Dispatchers.IO) {
        getData(op = if (reply == "0") "4" else "4_2", p1 = post, p3 = content, p4 = reply) { true }
    }

    suspend fun post(
        title: String,
        category: Post.Category,
        content: String,
        anonymousType: NameTheme,
        random: Boolean
    ) = withContext(Dispatchers.IO) {
        getData(
            op = "3",
            p1 = title,
            p2 = category.id.toString(),
            p3 = content,
            p4 = anonymousType.id,
            p5 = (if (random) Random.nextInt(1000000) else 0).toString()
        ) { true }
    }

    suspend fun report(id: String) = withContext(Dispatchers.IO) {
        getData(op = "e", p1 = id) { true }
    }
}
