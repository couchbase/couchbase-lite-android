package com.couchbase.lite;

import com.couchbase.lite.support.Base64;
import com.couchbase.test.lite.*;

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.router.*;
import com.couchbase.lite.router.Router;
import com.couchbase.lite.storage.Cursor;
import com.couchbase.lite.support.FileDirUtils;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.io.FileOutputStream;

public abstract class LitePerfTestCase extends LiteTestCase {

    public static final String TAG = "summary";
    JSONObject perfConfig, envConfig;
    public String replicationServer;
    public int replicationPort;
    public String replicationDatabase;

    @Override
    protected void setUp() throws Exception {
        Log.v(TAG, "setUp in LitePerfTestCase");
        //super.setUp();
        loadConfigs();
        runMultiple();
    }

    public double runOne(int numberOfDocuments, int sizeOfDocuments)  throws Exception {
        return 0;
    };

    public void loadConfigs() {
        JSONObject json;
        try {
            InputStream is = getAsset("config.json");
            perfConfig = new JSONObject(IOUtils.toString(is, "UTF-8"));
            envConfig = perfConfig.getJSONObject("environment");
            replicationServer = new String(envConfig.getString("sync_gateway_ip"));
            replicationPort = envConfig.getInt("sync_gateway_port");
            replicationDatabase = new String(envConfig.getString("sync_gateway_db"));
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
            return;
        }
        catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
        catch (JSONException ex) {
            ex.printStackTrace();
            return;
        }
        return;
    }

    protected String getReplicationProtocol() {
        return new String("http");
    }

    protected String getReplicationServer() {
        return replicationServer;
    }

    protected int getReplicationPort() {
        return replicationPort;
    }

    protected String getReplicationAdminUser() {
        return System.getProperty("replicationAdminUser");
    }

    protected String getReplicationAdminPassword() {
        return System.getProperty("replicationAdminPassword");
    }

    protected String getReplicationDatabase() {
        return replicationDatabase;
    }

     void runMultiple() {
        JSONObject testConfig;
        Integer repeat_count;
        ArrayList<Integer> arrayNumberOfDocuments, arraySizeofDocuments;
        ArrayList<ArrayList> arrayKpiNumbers, arrayBaselines;
        int repeatCount;
        double SumKpiBaseline;
        String className;
        try {
            String str = new String(getClass().getName());
            String[] tempArray = str.split("\\.");
            className = tempArray[tempArray.length - 1];
            testConfig = perfConfig.getJSONObject(className);
            arrayNumberOfDocuments = new ArrayList<Integer>();
            JSONArray jsonArray = testConfig.getJSONArray(new String("numbers_of_documents"));
            for (int i = 0; i < jsonArray.length(); i++)
                arrayNumberOfDocuments.add(new Integer(jsonArray.getInt(i)));
            arraySizeofDocuments = new ArrayList<Integer>();
            jsonArray = testConfig.getJSONArray(new String("sizes_of_document"));
            for (int i = 0; i < jsonArray.length(); i++)
                arraySizeofDocuments.add(new Integer(jsonArray.getInt(i)));
            arrayKpiNumbers = new ArrayList<ArrayList>();
            jsonArray = testConfig.getJSONArray(new String("kpi"));
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray oneRowJson = jsonArray.getJSONArray(i);
                ArrayList<Double> oneRow = new ArrayList<Double>();
                for (int j = 0; j < oneRowJson.length(); j++) {
                    oneRow.add(oneRowJson.getDouble(j));
                }
                arrayKpiNumbers.add(oneRow);
            }
            arrayBaselines = new ArrayList<ArrayList>();
            jsonArray = testConfig.getJSONArray(new String("baseline"));
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray oneRowJson = jsonArray.getJSONArray(i);
                ArrayList<Double> oneRow = new ArrayList<Double>();
                for (int j = 0; j < oneRowJson.length(); j++) {
                    oneRow.add(oneRowJson.getDouble(j));
                }
                arrayBaselines.add(oneRow);
            }
            repeatCount  =  testConfig.getInt(new String("repeat_count"));
            SumKpiBaseline  =  testConfig.getDouble(new String("sum_kpi_baseline"));
        }
        catch (JSONException ex) {
            ex.printStackTrace();
            return;
        }
        boolean kpiIsTotal = false;
        try {
            kpiIsTotal  =  testConfig.getBoolean(new String("kpi_is_total"));
        } catch (JSONException ex) {
            // It is ok if "kpi_is_total" is not specified
        }

        Log.v("PerformanceStats",TAG+","+"------------- "+className+" - Count of params: " + arrayNumberOfDocuments.size() + " NumberOfDocuments, " +
                arraySizeofDocuments.size() + " SizeOfDocuments " );

        ArrayList<ArrayList> resultNumberOfDocuments = new ArrayList<ArrayList>();
        ArrayList<ArrayList> diffBaselinesNumberofDocuments = new ArrayList<ArrayList>();
        int failCount = 0;
        int testCount = 0;
        double sumKpi = 0;
        Object object;

        for (int arrayNumbers = 0; arrayNumbers < arrayNumberOfDocuments.size(); arrayNumbers++) {
            int kNumberOfDocuments = arrayNumberOfDocuments.get(arrayNumbers);
            ArrayList<Double> arrayKpiRow = new ArrayList<Double>(arrayKpiNumbers.get(arrayNumbers));
            ArrayList<Double> arrayBaselineRow = new ArrayList<Double>(arrayBaselines.get(arrayNumbers));
            ArrayList<Double> resultSizeOfDocuments = new ArrayList<Double>();
            ArrayList<Double> diffBaselinesSizeofDocument = new ArrayList<Double>();

            for (int arraySizes = 0; arraySizes < arraySizeofDocuments.size(); arraySizes++) {
                int kSizeOfDocuments = arraySizeofDocuments.get(arraySizes);
                double kBaseline = arrayBaselineRow.get(arraySizes);
                double kpiTotalTime = arrayKpiRow.get(arraySizes);
                testCount++;

                if (kpiTotalTime < 0) {
                    resultSizeOfDocuments.add(-1.0);
                    diffBaselinesSizeofDocument.add(-1.0);
                    Log.v("PerformanceStats", "#"+testCount+": skpi" +
                            ". (docs="+kNumberOfDocuments+",size="+kSizeOfDocuments+"B) ");
                    continue;
                }

                ArrayList<Double> arrayResults = new ArrayList<Double>();
                double sum = 0, min = 999999, max = 0, avg = 0;
                for (int repeat = 0; repeat < repeatCount; repeat++) {
                    try {
                        //Force close and reopen of manager and database to ensure cold start before the iteration
                        tearDown();
                        Thread.sleep(1000);
                        startCBLite();
                        startDatabase();
                        //manager = new Manager(new LiteTestContext(), Manager.DEFAULT_OPTIONS);
                        //database = manager.getDatabase(DEFAULT_TEST_DB);
                        //Run test
                        double ExecutionTime = runOne(kNumberOfDocuments, kSizeOfDocuments);
                        arrayResults.add(ExecutionTime);
                        sum += ExecutionTime;
                        if (ExecutionTime < min) min = ExecutionTime;
                        if (ExecutionTime > max) max = ExecutionTime;
                    } catch (CouchbaseLiteException ex) {
                        ex.printStackTrace();
                        fail();
                    } catch(InterruptedException ex) {
                        Log.e(TAG, "Fail in Thread.sleep()", ex);
                        fail();
                    } catch(Exception ex) {
                        Log.e(TAG, "Fail to teardown and restart db", ex);
                        fail();
                    }
                }
                avg = sum / arrayResults.size();
                double result;
                if (kpiIsTotal)
                    result = min;
                else
                    result = min / kNumberOfDocuments;
                sumKpi = sumKpi + result;
                resultSizeOfDocuments.add(result);
                double diffBaseline = (result - kBaseline)/kBaseline*100;
                diffBaselinesSizeofDocument.add(diffBaseline);

                String passFail;
                if (result > kpiTotalTime || diffBaseline > 20 ) {
                    passFail = new String("Fail");
                    failCount++;
                } else
                    passFail = new String("Pass");
                Log.v("PerformanceStats", "#"+testCount+": "+passFail+
                      ". (docs="+kNumberOfDocuments+",size="+kSizeOfDocuments+"B) "+
                      "avg "+String.format("%.2f",avg)+", max "+String.format("%.2f",max)+", min "+String.format("%.2f",min) +
                      ", result "+String.format("%.2f",result)+"\nkpi "+String.format("%.2f",kpiTotalTime)+
                      ", baseline "+String.format("%.2f",kBaseline)+", "+"diffBaseline "+String.format("%.2f",diffBaseline)+
                        "％, RepeatExecutionTime:"+arrayResults.toString());
            }
            resultNumberOfDocuments.add(resultSizeOfDocuments);
            diffBaselinesNumberofDocuments.add(diffBaselinesSizeofDocument);
        }
        // This is the number for easier comparison between test runs to see whether there are over 10% variation.  The number does not have meaning of its own because it is the sum of all test iterations
        double diffPercent = (sumKpi - SumKpiBaseline)/SumKpiBaseline*100;
        String summaryPassFail = (failCount == 0) ? "PASS" : "FAIL";
        String baselineComparePassFail = (diffPercent > 10) ? "FAIL" : "PASS";

        Log.v("PerformanceStats", TAG + "," + className + ": "+summaryPassFail+". "+testCount+" sub-tests ran. "+failCount+" sub-tests fail");
        Log.v("PerformanceStats", TAG + "," + "Baseline compare "+baselineComparePassFail+". sumKpi:"+String.format("%.2f",sumKpi)+
                " baseline:"+String.format("%.2f",SumKpiBaseline)+" difference:"+String.format("%.2f",diffPercent)+"%");

        StringBuffer columHeader = new StringBuffer(" # docs; ");
        for (int arrayNumbers = 0; arrayNumbers < arraySizeofDocuments.size();  arrayNumbers++) {
            columHeader.append(arraySizeofDocuments.get(arrayNumbers) + "B, ");
        }
        Log.v("PerformanceStats", TAG + "," + columHeader);

        for (int i = 0; i < arrayNumberOfDocuments.size(); i++) {
            ArrayList<Double> row  = resultNumberOfDocuments.get(i);
            StringBuffer str = new StringBuffer();
            str.append(arrayNumberOfDocuments.get(i)).append("; ");
            for (int j = 0; j < row.size(); j++) {
                str.append(String.format("%.2f",row.get(j))).append("; ");
            }
            Log.v("PerformanceStats", TAG + "," + str + ";");
        }
        Log.v("PerformanceStats", TAG + "," + "--- Percentage of deviation from baselines");
        Log.v("PerformanceStats", TAG + "," + columHeader);
        for (int i = 0; i < arrayNumberOfDocuments.size(); i++) {
            ArrayList<Double> row  = diffBaselinesNumberofDocuments.get(i);
            StringBuffer str = new StringBuffer();
            str.append(arrayNumberOfDocuments.get(i)).append("; ");
            for (int j = 0; j < row.size(); j++) {
                if (row.get(j) == 1.0)
                    str.append("SKIP; ");
                else
                    str.append(String.format("%.2f",row.get(j))).append("%; ");

            }
            Log.v("PerformanceStats", TAG + "," + str + ";");
        }
    }

    public boolean isSyncGateway(URL remote) {
        return (remote.getPort() == 4984 || remote.getPort() == 4984);
    }

    public void addDocWithId(String docId, Map<String, Object> props, String attachmentName, boolean gzipped) throws IOException, CouchbaseLiteException {
        final String docJson;

        if (attachmentName == null) {
            Document doc = database.getDocument(docId);
            doc.putProperties(props);
        } else {
            // add attachment to document
            InputStream attachmentStream = getAsset(attachmentName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(attachmentStream, baos);
            if (gzipped == false) {
                String attachmentBase64 = Base64.encodeBytes(baos.toByteArray());
                Map<String, Object> attachment = new HashMap<String, Object>();
                attachment.put("content_type", "image/png");
                attachment.put("data", attachmentBase64);
                Map<String, Object> attachments = new HashMap<String, Object>();
                attachments.put(attachmentName, attachment);
                props.put("_attachments", attachments);
                Document doc = database.getDocument(docId);
                doc.putProperties(props);
            } else {
                byte[] bytes = baos.toByteArray();
                String attachmentBase64 = Base64.encodeBytes(bytes, Base64.GZIP);
                Map<String, Object> attachment = new HashMap<String, Object>();
                attachment.put("content_type", "image/png");
                attachment.put("data", attachmentBase64);
                attachment.put("encoding", "gzip");
                attachment.put("length", bytes.length);

                Map<String, Object> attachments = new HashMap<String, Object>();
                attachments.put(attachmentName, attachment);
                props.put("_attachments", attachments);
                Document doc = database.getDocument(docId);
                doc.putProperties(props);
            }
        }
    }

}
