package org.md2k.phonesensor.phone.sensors;

import android.content.Context;
import android.os.Handler;

import org.md2k.datakitapi.DataKitApi;
import org.md2k.datakitapi.datatype.DataTypeFloat;
import org.md2k.datakitapi.source.datasource.DataSource;
import org.md2k.datakitapi.source.datasource.DataSourceBuilder;
import org.md2k.datakitapi.source.datasource.DataSourceType;
import org.md2k.datakitapi.time.DateTime;
import org.md2k.phonesensor.BCMRecord;
import org.md2k.phonesensor.phone.CallBack;
import org.md2k.utilities.Report.Log;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
public class CPU extends PhoneSensorDataSource {
    private static final String TAG = CPU.class.getSimpleName();
    Handler scheduler;


    public CPU(Context context, boolean enabled) {
        super(context, DataSourceType.CPU, enabled);
        frequency="1.0 Hz";
    }


    public void unregister() {
        scheduler.removeCallbacks(statusCPU);
        scheduler=null;

//        context.unregisterReceiver(batteryInfoReceiver);
    }

    public void register(DataSourceBuilder dataSourceBuilder, CallBack newCallBack) {
        super.register(dataSourceBuilder, newCallBack);
        scheduler=new Handler();
        scheduler.post(statusCPU);
    }
    private void readUsage(long[] values) {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" +");  // Split on one or more spaces

            values[0] = Long.parseLong(toks[4]);
            values[1] = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);
            reader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    private Runnable statusFirst=new Runnable() {
        @Override
        public void run() {

        }
    };
    long curValues[]=new long[2];
    private Runnable statusCPU =new Runnable(){
        @Override
        public void run() {
            long values[]=new long[2];
            readUsage(values);
            float samples = (float)(values[1] - curValues[1]) / (float)((values[1] + values[0]) - (curValues[1] + curValues[0]));

            curValues=values;
            DataTypeFloat dataTypeFloat=new DataTypeFloat(DateTime.getDateTime(),samples);
            dataKitHandler.insert(dataSourceClient, dataTypeFloat);
            BCMRecord.getInstance().saveDataToTextFile(DataSourceType.CPU, dataTypeFloat);

            callBack.onReceivedData(dataTypeFloat);
            scheduler.postDelayed(statusCPU,1000);
        }
    };
}
