package com.algorand.utils;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.DB;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

public class MongoUtils{
	private static MongoClientURI uri = new MongoClientURI(
				"mongodb://localhost");
	private static MongoClient mongoClient = new MongoClient(uri);

	public static DB getDatabase(String name){
		DB database = mongoClient.getDB(name);
		return database;
	}
}
