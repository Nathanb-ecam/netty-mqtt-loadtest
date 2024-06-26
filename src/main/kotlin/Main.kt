package org.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.netty.handler.codec.mqtt.MqttQoS
import org.example.org.example.MqttCredentials
import org.slf4j.event.Level
import java.util.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.example.mqtt.MqttPayload


fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(CallLogging) {
            level = Level.INFO
        }

        install(CORS) {
            allowHeader(HttpHeaders.ContentType)
            anyHost()
        }
        val token = generateToken()
        println("gen : ${token}")
        val broker_ip = System.getenv("BROKER_IP") ?: "localhost"
        val brokerPortString: String? = System.getenv("BROKER_PORT")
        val broker_port: Int = brokerPortString?.toIntOrNull() ?: 1883
        val topic = System.getenv("BROKER_TOPIC") ?: "test"

        val clientName = System.getenv("MQTT_USERNAME") ?: ""
        val clientPassword = System.getenv("MQTT_PASSWORD") ?: ""
        val mqttCredentials = MqttCredentials(clientName,clientPassword)





        routing {
            get("/") {
                call.respondText("Hello, Ktor!")
            }
            post("/alert"){
                println("Alert")
                call.respondText("Alert")
            }
            post("/launch") {
                var collectTest = false

                val parameters = call.receiveParameters()
                var status = "none"

                val receivedToken = parameters["token"]
                val messagePayloadSize = parameters["messagePayloadSize"]?.toIntOrNull() ?: 0
                val messagePayloadString = parameters["messagePayloadString"] ?: ""

                val nMessagesPerChannel = parameters["nMessagesPerChannel"]?.toIntOrNull() ?: 1000
                val channelsPerGroup = parameters["channelsPerGroup"]?.toIntOrNull() ?: 100
                val amountOfGroups = parameters["amountOfGroups"]?.toIntOrNull() ?: 10

                val keepAliveSeconds = parameters["keepAliveSeconds"]?.toIntOrNull() ?: 15
                val qos = parameters["qos"]?.toIntOrNull() ?: 0
                val mqttQos = MqttQoS.valueOf(qos)

                val cid = parameters["mqttCid"] ?: ""
                val mqttToken = parameters["mqttToken"] ?: ""


                val mqttPayload = MqttPayload(
                    cid = cid,
                    token = mqttToken
                )



                if(receivedToken == token){
                    if(messagePayloadString.isNotEmpty()) collectTest = true
                    if(messagePayloadString.isEmpty() && messagePayloadSize == 0 ) {call.respondText("Need to provide payloadString or the amount of bytes for the payload ")}
                    else{
                        val loadConfig: LoadConfig?
                        if(collectTest){
                            loadConfig = LoadConfig(
                                payload = mqttPayload,
                                keepAliveSec = keepAliveSeconds,
                                qos = mqttQos,
//                            messagePayload = encryptedSelf.toByteArray(Charsets.UTF_8),
                                messagePayloadString = messagePayloadString,

                                nMessagesPerChannel = nMessagesPerChannel,
                                channelsPerGroup = channelsPerGroup,
                                amountOfGroups = amountOfGroups
                            )
                        }else{
                            loadConfig = LoadConfig(
                                keepAliveSec = keepAliveSeconds,
                                qos = mqttQos,
                                messagePayloadSize = messagePayloadSize,
                                nMessagesPerChannel = nMessagesPerChannel,
                                channelsPerGroup = channelsPerGroup,
                                amountOfGroups = amountOfGroups
                            )
                        }

                        val loadTest = LoadTester(
                            broker = broker_ip,
                            port = broker_port,
                            topic = topic,
                            mqttCredentials = mqttCredentials,
                            loadConfig
                        )
                        loadTest.launch()

                        val info = MessageInfoMetrics(loadConfig)
                        val parametersInfo = info.loadParameters()
                        println(parametersInfo)
                        status = "Launched ${info.getTheoreticalMessageCount()} messages on $broker_ip:$broker_port, topic : $topic ,  username: ${mqttCredentials.clientName}\n"
                    }


                }else{
                    call.respondText("invalid token")
                }

                call.respondText(status)
            }







        }
    }.start(wait = true)
}


fun generateToken(): String {
    val random = Random()
    val bytes = ByteArray(64)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}







