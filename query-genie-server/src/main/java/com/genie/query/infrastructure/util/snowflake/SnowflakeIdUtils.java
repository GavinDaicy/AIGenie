package com.genie.query.infrastructure.util.snowflake;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * @ClassName SnowflakeIdUtils
 **/
@Component
@SuppressWarnings("all")
public class SnowflakeIdUtils {

    private static SnowflakeIdWorker snowflakeIdWorker;
    @PostConstruct
    public void init(){
        long dataId=2;
        long workId;
        while (true){
            double roundNum = randomNumber(11);
            long times= System.currentTimeMillis();
            double dival = times / roundNum;
            workId= Math.round(dival);
            if(workId <=255) {
                break;
            }
        }
        snowflakeIdWorker = new SnowflakeIdWorker(dataId,workId);
    }

    public static Long getNextId(){
        return snowflakeIdWorker.nextId();
    }

    public static String getNextStringId(){
        return snowflakeIdWorker.nextString();
    }

    public static List<Long> getNextId(int size){
        return snowflakeIdWorker.nextId(size);
    }

    public static String[] getNextStringId(int size){
        return snowflakeIdWorker.nextString(size);
    }

    /**
     * 此方法可以在前缀上增加业务标志
     * @param prefix
     * @return
     */
    public static String nextCode(String prefix){
        return  snowflakeIdWorker.nextCode(prefix);
    }
    /**
     * 此方法可以在前缀上增加业务标志
     * @param prefix
     * @param nums
     * @return
     */
    public String[] nextCode(String prefix, int nums){
        return snowflakeIdWorker.nextCode(prefix,nums);
    }

    public static long randomNumber(int length) {
        Long sed = (long)(Math.random() * Math.pow(10, length));
        if(sed.toString().length() < length) {
            sed += (long)Math.pow(10, length - 1);
        }
        return sed;
    }

    /**
     * 获取UUID
     * @return
     */
    public static String getSystemUuid(){
        String uuid = "";
        synchronized (uuid) {
            uuid = UUID.randomUUID().toString();
            uuid = uuid.replace("-", "");
        }
        return uuid;
    }

    public static void main(String[] args){
//        for(int i=0;i<1000;i++){
//            long bs=SnowflakeIdUtils.getNextId();
//            System.out.println(bs);
//        }

        SnowflakeIdUtils utils = new SnowflakeIdUtils();
        utils.init();
        System.out.println(SnowflakeIdUtils.getNextId(1));
    }

}
