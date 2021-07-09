/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.network.translators.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPluginMessagePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPluginMessagePacket;
import com.google.common.base.Charsets;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.common.AuthType;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;
import org.geysermc.cumulus.Form;
import org.geysermc.cumulus.Forms;
import org.geysermc.cumulus.util.FormType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Translator(packet = ServerPluginMessagePacket.class)
public class JavaPluginMessageTranslator extends PacketTranslator<ServerPluginMessagePacket> {
    @Override
    public void translate(ServerPluginMessagePacket packet, GeyserSession session) {
        String channel = packet.getChannel();

        if (channel.equals("geyser:settings")) {
            try {
                JsonNode data = GeyserConnector.JSON_MAPPER.readTree(packet.getData());

                if (data.get("success").asBoolean()) {
                    JsonNode settings = data.get("settings");
                    JsonNode disableBedrockScaffolding = settings.get("disable-bedrock-scaffolding");
                    if (disableBedrockScaffolding != null) {
                        session.getWorldCache().setDisableBedrockScaffolding(disableBedrockScaffolding.asBoolean() || session.getConnector().getConfig().isDisableBedrockScaffolding());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // The only plugin messages it has to listen for at this point are Floodgate plugin messages
        if (session.getConnector().getDefaultAuthType() != AuthType.FLOODGATE) {
            return;
        }

        if (channel.equals("floodgate:form")) {
            byte[] data = packet.getData();

            // receive: first byte is form type, second and third are the id, remaining is the form data
            // respond: first and second byte id, remaining is form response data

            FormType type = FormType.getByOrdinal(data[0]);
            if (type == null) {
                throw new NullPointerException(
                        "Got type " + data[0] + " which isn't a valid form type!");
            }

            String dataString = new String(data, 3, data.length - 3, Charsets.UTF_8);

            Form form = Forms.fromJson(dataString, type);
            form.setResponseHandler(response -> {
                byte[] raw = response.getBytes(StandardCharsets.UTF_8);
                byte[] finalData = new byte[raw.length + 2];

                finalData[0] = data[1];
                finalData[1] = data[2];
                System.arraycopy(raw, 0, finalData, 2, raw.length);

                session.sendDownstreamPacket(new ClientPluginMessagePacket(channel, finalData));
            });
            session.sendForm(form);
        }
    }
}