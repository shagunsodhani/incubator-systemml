/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.test.integration.functions.mlcontext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.hadoop.io.LongWritable;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.types.StructType;
import org.apache.sysml.api.DMLException;
import org.apache.sysml.api.DMLScript;
import org.apache.sysml.api.DMLScript.RUNTIME_PLATFORM;
import org.apache.sysml.api.MLContext;
import org.apache.sysml.api.MLContextProxy;
import org.apache.sysml.api.MLOutput;
import org.apache.sysml.parser.DataExpression;
import org.apache.sysml.parser.Expression.ValueType;
import org.apache.sysml.parser.ParseException;
import org.apache.sysml.runtime.DMLRuntimeException;
import org.apache.sysml.runtime.controlprogram.context.SparkExecutionContext;
import org.apache.sysml.runtime.instructions.spark.utils.FrameRDDConverterUtils;
import org.apache.sysml.runtime.instructions.spark.utils.FrameRDDConverterUtils.LongFrameToLongWritableFrameFunction;
import org.apache.sysml.runtime.matrix.MatrixCharacteristics;
import org.apache.sysml.runtime.matrix.data.CSVFileFormatProperties;
import org.apache.sysml.runtime.matrix.data.FrameBlock;
import org.apache.sysml.runtime.matrix.data.InputInfo;
import org.apache.sysml.runtime.matrix.data.OutputInfo;
import org.apache.sysml.runtime.util.MapReduceTool;
import org.apache.sysml.runtime.util.UtilFunctions;
import org.apache.sysml.test.integration.AutomatedTestBase;
import org.apache.sysml.test.integration.TestConfiguration;
import org.apache.sysml.test.utils.TestUtils;
import org.junit.Assert;
import org.junit.Test;


public class FrameTest extends AutomatedTestBase 
{
	private final static String TEST_DIR = "functions/frame/";
	private final static String TEST_NAME = "FrameGeneral";
	private final static String TEST_CLASS_DIR = TEST_DIR + FrameTest.class.getSimpleName() + "/";

	private final static int min=0;
	private final static int max=100;
	private final static int  rows = 2245;
	private final static int  cols = 1264;
	
	private final static double sparsity1 = 1.0;
	private final static double sparsity2 = 0.35;
	
	private final static double epsilon=0.0000000001;


	private final static List<ValueType> schemaMixedLargeListStr = Collections.nCopies(cols/4, ValueType.STRING);
	private final static List<ValueType> schemaMixedLargeListDble  = Collections.nCopies(cols/4, ValueType.DOUBLE);
	private final static List<ValueType> schemaMixedLargeListInt  = Collections.nCopies(cols/4, ValueType.INT);
	private final static List<ValueType> schemaMixedLargeListBool  = Collections.nCopies(cols/4, ValueType.BOOLEAN);
	private static ValueType[] schemaMixedLarge = null;
	static {
		final List<ValueType> schemaMixedLargeList = new ArrayList<ValueType>(schemaMixedLargeListStr);
		schemaMixedLargeList.addAll(schemaMixedLargeListDble);
		schemaMixedLargeList.addAll(schemaMixedLargeListInt);
		schemaMixedLargeList.addAll(schemaMixedLargeListBool);
		schemaMixedLarge = new ValueType[schemaMixedLargeList.size()];
		schemaMixedLarge = (ValueType[]) schemaMixedLargeList.toArray(schemaMixedLarge);
	}
	 
	@Override
	public void setUp() {
		addTestConfiguration(TEST_NAME, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME, 
				new String[] {"AB", "C"}));
	}
	
	@Test
	public void testCSVInCSVOut() throws IOException, DMLException, ParseException {
		testFrameGeneral(InputInfo.CSVInputInfo, OutputInfo.CSVOutputInfo);
	}
	
	@Test
	public void testCSVInTextOut() throws IOException, DMLException, ParseException {
		testFrameGeneral(InputInfo.TextCellInputInfo, OutputInfo.CSVOutputInfo);
	}
	
	@Test
	public void testTextInCSVOut() throws IOException, DMLException, ParseException {
		testFrameGeneral(InputInfo.CSVInputInfo, OutputInfo.TextCellOutputInfo);
	}
	
	@Test
	public void testTextInTextOut() throws IOException, DMLException, ParseException {
		testFrameGeneral(InputInfo.TextCellInputInfo, OutputInfo.TextCellOutputInfo);
	}
	
	@Test
	public void testDataFrameInCSVOut() throws IOException, DMLException, ParseException {
		testFrameGeneral(InputInfo.CSVInputInfo, true, false);
	}
	
	@Test
	public void testDataFrameInTextOut() throws IOException, DMLException, ParseException {
		testFrameGeneral(InputInfo.TextCellInputInfo, true, false);
	}
	
	@Test
	public void testDataFrameInDataFrameOut() throws IOException, DMLException, ParseException {
		testFrameGeneral(true, true);
	}
	
	private void testFrameGeneral(InputInfo iinfo, OutputInfo oinfo) throws IOException, DMLException, ParseException {
		testFrameGeneral(iinfo, oinfo, false, false);
	}
	
	private void testFrameGeneral(InputInfo iinfo, boolean bFromDataFrame, boolean bToDataFrame) throws IOException, DMLException, ParseException {
		testFrameGeneral(iinfo, OutputInfo.CSVOutputInfo, bFromDataFrame, bToDataFrame);
	}
	
	private void testFrameGeneral(boolean bFromDataFrame, boolean bToDataFrame) throws IOException, DMLException, ParseException {
		testFrameGeneral(InputInfo.BinaryBlockInputInfo, OutputInfo.CSVOutputInfo, bFromDataFrame, bToDataFrame);
	}
	
	private void testFrameGeneral(InputInfo iinfo, OutputInfo oinfo, boolean bFromDataFrame, boolean bToDataFrame) throws IOException, DMLException, ParseException {
		
		boolean oldConfig = DMLScript.USE_LOCAL_SPARK_CONFIG; 
		DMLScript.USE_LOCAL_SPARK_CONFIG = true;
		RUNTIME_PLATFORM oldRT = DMLScript.rtplatform;
		DMLScript.rtplatform = RUNTIME_PLATFORM.HYBRID_SPARK;

		this.scriptType = ScriptType.DML;
		
		int rowstart = 234, rowend = 1478, colstart = 125, colend = 568;
		int bRows = rowend-rowstart+1, bCols = colend-colstart+1;

		int rowstartC = 124, rowendC = 1178, colstartC = 143, colendC = 368;
		int cRows = rowendC-rowstartC+1, cCols = colendC-colstartC+1;
		
		HashMap<String, ValueType[]> outputSchema = new HashMap<String, ValueType[]>();
		HashMap<String, MatrixCharacteristics> outputMC = new HashMap<String, MatrixCharacteristics>();
		
		TestConfiguration config = getTestConfiguration(TEST_NAME);
		
		loadTestConfiguration(config);

		List<String> proArgs = new ArrayList<String>();
		proArgs.add(input("A"));
		proArgs.add(Integer.toString(rows));
		proArgs.add(Integer.toString(cols));
		proArgs.add(input("B"));
		proArgs.add(Integer.toString(bRows));
		proArgs.add(Integer.toString(bCols));
		proArgs.add(Integer.toString(rowstart));
		proArgs.add(Integer.toString(rowend));
		proArgs.add(Integer.toString(colstart));
		proArgs.add(Integer.toString(colend));
		proArgs.add(output("A"));
		proArgs.add(Integer.toString(rowstartC));
		proArgs.add(Integer.toString(rowendC));
		proArgs.add(Integer.toString(colstartC));
		proArgs.add(Integer.toString(colendC));
		proArgs.add(output("C"));
		programArgs = proArgs.toArray(new String[proArgs.size()]);
		
		fullDMLScriptName = SCRIPT_DIR + TEST_DIR + TEST_NAME + ".dml";

		ValueType[] schema = schemaMixedLarge;
		
		//initialize the frame data.
		List<ValueType> lschema = Arrays.asList(schema);

		fullRScriptName = SCRIPT_DIR + TEST_DIR + TEST_NAME + ".R";
		rCmd = "Rscript" + " " + fullRScriptName + " " + 
			inputDir() + " " + rowstart + " " + rowend + " " + colstart + " " + colend + " " + expectedDir()
					  + " " + rowstartC + " " + rowendC + " " + colstartC + " " + colendC;
		
		double sparsity=sparsity1;//rand.nextDouble(); 
        double[][] A = getRandomMatrix(rows, cols, min, max, sparsity, 1111 /*\\System.currentTimeMillis()*/);
        writeInputFrameWithMTD("A", A, true, schema, oinfo);	        
        
        sparsity=sparsity2;//rand.nextDouble();
        double[][] B = getRandomMatrix((int)(bRows), (int)(bCols), min, max, sparsity, 2345 /*System.currentTimeMillis()*/);
        //Following way of creation causes serialization issue in frame processing
        //List<ValueType> lschemaB = lschema.subList((int)colstart-1, (int)colend); 
        ValueType[] schemaB = new ValueType[bCols];
        for (int i = 0; i < bCols; ++i)
        	schemaB[i] = schema[colstart-1+i];
		List<ValueType> lschemaB = Arrays.asList(schemaB);
        writeInputFrameWithMTD("B", B, true, schemaB, oinfo);	        

        ValueType[] schemaC = new ValueType[colendC-colstartC+1];
        for (int i = 0; i < cCols; ++i)
        	schemaC[i] = schema[colstartC-1+i];
        
		MLContext mlCtx = getMLContextForTesting();
		SparkContext sc = mlCtx.getSparkContext();
		JavaSparkContext jsc = new JavaSparkContext(sc);
		
		DataFrame dfA = null, dfB = null; 
		if(bFromDataFrame)
		{
			//Create DataFrame for input A 
			SQLContext sqlContext = new SQLContext(sc);
			StructType dfSchemaA = FrameRDDConverterUtils.convertFrameSchemaToDFSchema(schema, false);
			JavaRDD<Row> rowRDDA = FrameRDDConverterUtils.csvToRowRDD(jsc, input("A"), DataExpression.DEFAULT_DELIM_DELIMITER, schema);
			dfA = sqlContext.createDataFrame(rowRDDA, dfSchemaA);
			
			//Create DataFrame for input B 
			StructType dfSchemaB = FrameRDDConverterUtils.convertFrameSchemaToDFSchema(schemaB, false);
			JavaRDD<Row> rowRDDB = FrameRDDConverterUtils.csvToRowRDD(jsc, input("B"), DataExpression.DEFAULT_DELIM_DELIMITER, schemaB);
			dfB = sqlContext.createDataFrame(rowRDDB, dfSchemaB);
		}

		try 
		{
			mlCtx.reset(true); // Cleanup config to ensure future MLContext testcases have correct 'cp.parallel.matrixmult'
			
			String format = "csv";
			if(oinfo == OutputInfo.TextCellOutputInfo)
				format = "text";

			if(bFromDataFrame)
				mlCtx.registerFrameInput("A", dfA, false);
			else {
				JavaRDD<String> aIn =  jsc.textFile(input("A"));
				mlCtx.registerInput("A", aIn, format, rows, cols, new CSVFileFormatProperties(), lschema);
			}

			if(bFromDataFrame)
				mlCtx.registerFrameInput("B", dfB, false);
			else {
				JavaRDD<String> bIn =  jsc.textFile(input("B"));
				mlCtx.registerInput("B", bIn, format, bRows, bCols, new CSVFileFormatProperties(), lschemaB);
			}

			// Output one frame to HDFS and get one as RDD //TODO HDFS input/output to do
			mlCtx.registerOutput("A");
			mlCtx.registerOutput("C");
			
			MLOutput out = mlCtx.execute(fullDMLScriptName, programArgs);
			
			format = "csv";
			if(iinfo == InputInfo.TextCellInputInfo)
				format = "text";
			
			String fName = output("AB");
			try {
				MapReduceTool.deleteFileIfExistOnHDFS( fName );
			} catch (IOException e) {
				throw new DMLRuntimeException("Error: While deleting file on HDFS");
			}
			
			if(!bToDataFrame)
			{
				JavaRDD<String> aOut = out.getStringFrameRDD("A", format, new CSVFileFormatProperties());
				aOut.saveAsTextFile(fName);
			} else {
				DataFrame df = out.getDataFrameRDD("A", jsc);
				
				//Convert back DataFrame to binary block for comparison using original binary to converted DF and back to binary 
				MatrixCharacteristics mc = new MatrixCharacteristics(rows, cols, -1, -1, -1);
				JavaPairRDD<LongWritable, FrameBlock> rddOut = FrameRDDConverterUtils
						.dataFrameToBinaryBlock(jsc, df, mc, bFromDataFrame)
						.mapToPair(new LongFrameToLongWritableFrameFunction());
				rddOut.saveAsHadoopFile(output("AB"), LongWritable.class, FrameBlock.class, OutputInfo.BinaryBlockOutputInfo.outputFormatClass);
			}

			fName = output("C");
			try {
				MapReduceTool.deleteFileIfExistOnHDFS( fName );
			} catch (IOException e) {
				throw new DMLRuntimeException("Error: While deleting file on HDFS");
			} 
			if(!bToDataFrame)
			{
				JavaRDD<String> aOut = out.getStringFrameRDD("C", format, new CSVFileFormatProperties());
				aOut.saveAsTextFile(fName);
			} else {
				DataFrame df = out.getDataFrameRDD("C", jsc);
				
				//Convert back DataFrame to binary block for comparison using original binary to converted DF and back to binary 
				MatrixCharacteristics mc = new MatrixCharacteristics(cRows, cCols, -1, -1, -1);
				JavaPairRDD<LongWritable, FrameBlock> rddOut = FrameRDDConverterUtils
						.dataFrameToBinaryBlock(jsc, df, mc, bFromDataFrame)
						.mapToPair(new LongFrameToLongWritableFrameFunction());
				rddOut.saveAsHadoopFile(fName, LongWritable.class, FrameBlock.class, OutputInfo.BinaryBlockOutputInfo.outputFormatClass);
			}
			
			runRScript(true);
			
			outputSchema.put("AB", schema);
			outputMC.put("AB", new MatrixCharacteristics(rows, cols, -1, -1));
			outputSchema.put("C", schemaC);
			outputMC.put("C", new MatrixCharacteristics(cRows, cCols, -1, -1));
			
			for(String file: config.getOutputFiles())
			{
				MatrixCharacteristics md = outputMC.get(file);
				FrameBlock frameBlock = readDMLFrameFromHDFS(file, iinfo, md);
				FrameBlock frameRBlock = readRFrameFromHDFS(file+".csv", InputInfo.CSVInputInfo, md);
				ValueType[] schemaOut = outputSchema.get(file);
				verifyFrameData(frameBlock, frameRBlock, schemaOut);
				System.out.println("File " + file +  " processed successfully.");
			}

			//cleanup mlcontext (prevent test memory leaks)
			mlCtx.reset();
			
			System.out.println("Frame MLContext test completed successfully.");
		}
		finally {
			DMLScript.rtplatform = oldRT;
			DMLScript.USE_LOCAL_SPARK_CONFIG = oldConfig;

			if (sc != null) {
				sc.stop();
			}
			SparkExecutionContext.resetSparkContextStatic();
			MLContextProxy.setActive(false);
		}
	}
	
	private void verifyFrameData(FrameBlock frame1, FrameBlock frame2, ValueType[] schema) {
		for ( int i=0; i<frame1.getNumRows(); i++ )
			for( int j=0; j<frame1.getNumColumns(); j++ )	{
				Object val1 = UtilFunctions.stringToObject(schema[j], UtilFunctions.objectToString(frame1.get(i, j)));
				Object val2 = UtilFunctions.stringToObject(schema[j], UtilFunctions.objectToString(frame2.get(i, j)));
				if( TestUtils.compareToR(schema[j], val1, val2, epsilon) != 0)
					Assert.fail("The DML data for cell ("+ i + "," + j + ") is " + val1 + 
							", not same as the R value " + val2);
			}
	}

}