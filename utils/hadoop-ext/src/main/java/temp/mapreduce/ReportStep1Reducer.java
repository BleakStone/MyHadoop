package com.uc.ssp.baserpt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;

import com.uc.ssp.common.Calculater;
import com.uc.ssp.common.Const;
import com.uc.ssp.common.SequenceParser;
import com.uc.ssp.common.SequencePosition;
import com.uc.ssp.common.Util;

public class ReportStep1Reducer extends Reducer<Text, Text, Text, Text> {
	private Configuration conf = null;
	private BaseRptConf baseRptConf = null;
	
	@Override
	public void reduce(Text key, Iterable<Text> value, Context context) throws IOException, InterruptedException {

		Text outputKey = new Text();
		Text outputValue = new Text();
		StringBuilder outputValueStringBuilder = new StringBuilder();
		
		String keyString = new String(key.getBytes(), 0, key.getLength(), Const.DEFAULT_CHARACTER);		
		int index = keyString.indexOf(Const.SEQUENCE_FIELD_SEPARATE);
		if(index == -1){
			throw new IOException(String.format("Can not found report id, record: %s", keyString));
		}
		
		int rptId = 0;
		try{
			rptId = Integer.parseInt(keyString.substring(0, index));
		}catch(NumberFormatException e){
			throw new IOException(String.format("Unavailable report id: %s, %s", keyString.substring(index), e.toString()));
		}
		
		if(!baseRptConf.rptMap.containsKey(rptId)){
			throw new IOException(String.format("Undefined report id: %d", rptId));
		}
		RptInfo rptInfo = baseRptConf.rptMap.get(rptId);
		int uidTagIndex = rptInfo.dim.size() + 2;
		
		SequenceParser sequenceParser = new SequenceParser();
		SequencePosition pos = new SequencePosition();
		if(1 != sequenceParser.parser(keyString, uidTagIndex, uidTagIndex, pos)){
			throw new IOException(String.format("Can not found uid tag, record: %s", keyString));
		}
		outputKey.set(keyString.substring(0, pos.beginIndex));
		
		String uidTagString = sequenceParser.get(0);
		int uidTag = 0;
		try{
			uidTag = Integer.parseInt(uidTagString);
		}catch(NumberFormatException e){
			throw new IOException(String.format("Unavailable uid tag: %s, %s", uidTagString, e.toString()));
		}
		if(!rptInfo.meaMap.containsKey(uidTag)){
			throw new IOException(String.format("Undefined uid tag: %d", uidTag));
		}
		ArrayList<com.uc.ssp.common.Measure> meaList = rptInfo.meaMap.get(uidTag);
		
		long lastTime = System.currentTimeMillis();
		long currentTime = 0;
		Calculater calculater = new Calculater();
		Text thisValue = null;
		Iterator<Text> it = value.iterator();
		while (it.hasNext()) {
			thisValue = it.next();
			String valueString = new String(thisValue.getBytes(), 0, thisValue.getLength(), Const.DEFAULT_CHARACTER);
			if(meaList.size() != sequenceParser.parser(valueString)){
				throw new IOException(String.format("Error happened when parsing record: %s", valueString));
			}
			for(int i=0; i<meaList.size(); i++){
				try{
					calculater.add(meaList.get(i).statTypeFieldId, sequenceParser.get(i), meaList.get(i).algorithm);
				}catch(Exception e){
					throw new IOException(e.toString());
				}
			}
			currentTime = System.currentTimeMillis();
			if(currentTime-lastTime > Const.TIMEOUT_MILLIONSECONDS){
				lastTime = currentTime;
				context.progress();
				//context.setStatus(null);
			}
		}
		outputValueStringBuilder.append(uidTagString);
		outputValueStringBuilder.append(Const.SEQUENCE_FIELD_SEPARATE);
		for(int i=0; i<meaList.size(); i++){
			if(i != 0){
				outputValueStringBuilder.append(Const.SEQUENCE_FIELD_SEPARATE);
			}
			outputValueStringBuilder.append(calculater.get(meaList.get(i).statTypeFieldId));
		}
		outputValue.set(outputValueStringBuilder.toString());
		context.write(outputKey, outputValue);
	}
	
	@Override
	protected void setup(Context context) throws IOException {
		conf = context.getConfiguration();	
		try {
			baseRptConf = (BaseRptConf) Util.getObject(Util.decode(conf.get("baseRptConf")));
		} catch (ClassNotFoundException e) {
			throw new IOException(e.toString());
		}
	}
}
