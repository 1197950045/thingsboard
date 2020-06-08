package org.thingsboard.server.transport.http;

import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class DeviceApiControllerTest {

    @Test
    public void postZHC() {
        String str = "5504080FAF0F8B0F7700003898";
        String json = "{";
        for (int i = 0, j = 0; i < 16; i += 4, j++) {
            json += "\"data" + j + "\":\"" + new BigInteger(str.substring(6 + i, 10 + i), 16).toString() + "\"";
            if (i != 12) {
                json += ",";
            }
            System.out.println(json);
        }
        json += "}";
        System.out.println(json
        );
    }

    @Test
    public void postElectric() {
        String str = "5504080FB50F970F7E00008299";
        String json = "{";
        for (int i = 0, j = 0; i < 12; i += 4, j++) {
            json += "\"current" + (j + 1) + "\":\"" + new BigInteger(str.substring(6 + i, 10 + i), 16).toString() + "\"";
            if (i != 8) {
                json += ",";
            }
            System.out.println(json);
        }
        json += "}";
        System.out.println(json
        );
    }
}