package com.lipisoft.toyshark.packetRebuild;

import java.net.UnknownHostException;

/**
 * Class for supporting byte level operations.<br>
 * 1 .easy adding/getting numbers from<br>
 *    byte array at network order (AKA big-endian).<br>
 * <br>
 * 2. printing methods (ordering bytes into readable hex format)<br>
 * <br>
 * 3. transforming big-endian into little-endian and vice versa.<br>
 * <br>
 * In general, java programming language is problematic in dealing with low level representation.<br>
 * For example, no unsigned numbers. Therefore, in many cases we need to keep <br>
 * number in a bigger storage, for example, uint_32 must be handled as long.<br>
 * or else 0xFFFFFFFF will be treated as -1.
 * <br>
 * @author roni bar-yanai
 *
 */ 
public class ByteUtils
{
	private static final int IPV4_ADDRESS_LEN = 4;
	private static final int MAX_PACKET_SIZE = 1600;
	
	private char[] statArr = new char[MAX_PACKET_SIZE*10];
	
	/**
	 * The method pull out array of bytes and return them as a
	 *  readable string in mac address common format xx:xx:xx:xx:xx:xx
	 * @param theBytes - the packet
	 * @param startIdx start index
	 * @param endIdx end index
	 * @return the mas address as string.
	 */
	public String getAsMac(byte[] theBytes,int startIdx,int endIdx)
	{
		int idx=0;
		for(int i=startIdx ; i<endIdx ; i++)
		{
			if (i > startIdx )
			{
				statArr[idx++]=':';
			}
			int num = 0xff & theBytes[i];

			int second1 = (num & 0x0f);
			int first1 = ((num & 0xf0) >> 4);
			
			char second  = (char) (second1 < 10 ? '0' + second1 : 'A' + second1 - 10);
			char first = (char) (first1 < 10 ? '0' + first1 : 'A' + first1 - 10);
			
			statArr[idx++]=first;
			statArr[idx++]=second;
		}
		return new String(statArr,0,idx);
	}
	
	/**
	 * covert byte array to string in a hex readable format 
	 * @param thePacket packet stream
	 * @return the byte array as string (in hex).
	 */
	public String getAsString(byte[] thePacket)
	{
		return getAsString(thePacket,0,thePacket.length);
	}
	
	/**
	 * covert byte array slice to string in a hex readable format
	 * @param thePacket - the byte array.
	 * @param startIndex - start index of the slice.
	 * @param endIndex - end index of the slice.
	 * @return string
	 */
	private String getAsString(byte[] thePacket,int startIndex,int endIndex)
	{
		return getAsString(thePacket,startIndex,endIndex,16);
	}

	/**
	 * covert byte array slice to string in a hex readable format
	 * @param thePacket - the byte array.
	 * @param startidx - start index of the slice.
	 * @param endidx - end index of the slice.
	 * @param maxInLine - maximum chars per line
	 * @return string
	 */
	private String getAsString(byte[] thePacket,int startidx,int endidx,int maxInLine)
	{
		int idx=0;
		for(int i=startidx ; i<endidx ; i++)
		{
			if (i != 0 && i%4 == 0)
			{
				statArr[idx++]=' ';
				statArr[idx++]=' ';
				statArr[idx++]='-';
				statArr[idx++]=' ';
    		}
			if (i != 0 && i%maxInLine == 0)
			{
				statArr[idx++]='\r';
				statArr[idx++]='\n';
			}
			
			int num = 0xff & thePacket[i];

			int second1 = (num & 0x0f);
			int first1 = ((num & 0xf0) >> 4);
			
			char second  = (char) ((second1<10)?'0'+second1:'A'+second1-10); 
			char first = (char) ((first1<10)?'0'+first1:'A'+first1-10);
			
			statArr[idx++]=first;
			statArr[idx++]=second;
			statArr[idx++]=' ';
		}
		return new String(statArr,0,idx);	
	}
	
	/**
	 * turn 16 bits unsigned integer number to byte array representing the number
	 *  in network order.
	 * @param theNumber number to be changed to network stream
	 * @return byte array 
	 *  (int in java is 4 bytes here only the lower 2 bytes are counted)
	 */
	public static byte[] getAs_uint16_NetOrder(int theNumber)
	{
		byte[] toReturn = new byte[2];
		
		toReturn[0] = (byte) (theNumber & 0xf0);
		toReturn[1] = (byte) (theNumber & 0x0f);
		
		return toReturn;
	}
	
	/**
	 * turn 32 bits unsigned integer number to byte array representing the number
	 *  in network order.
	 * @param theNumber number to be changed to network stream
	 * @return byte array 
	 */
	public static byte[] getAs_uint32_NetOrder(int theNumber)
	{
		byte[] toReturn = new byte[4];
		
		toReturn[0] = (byte) ((theNumber >> 24) & 0xff0);
		toReturn[1] = (byte) ((theNumber >> 16) & 0xff);
		toReturn[2] = (byte) ((theNumber >> 8 )& 0xff);
		toReturn[3] = (byte) (theNumber & 0xff);
		
		return toReturn;
	}
	
	/**
	 * pull out a byte (unsigned) to int.
	 * @param theBytes - the byte array.
	 * @param idx - the byte location.
	 * @return the int value (0-255)
	 */
	public static int getByteNetOrderTo_uint8(byte[] theBytes,int idx)
	{
		return theBytes[idx] & 0xff;
	}
	
	/**
	 * pull out unsigned 16 bits integer out of the array.
	 * @param theBytes - the array
	 * @param idx - the starting index
	 * @return the num (0-65535)
	 */
	public static int getByteNetOrderTo_unit16(byte[] theBytes,int idx)
	{
		int sum = 0;
		for (int i=0 ; i<2 ; i++)
		{
			sum = (sum<<8) + (0xff & theBytes[i+idx]);
		}
		return sum;
	}
	
	/**
	 * pull out unsigned 16 bits integer out of the array.
	 * @param theBytes - the array
	 * @param idx - the starting index
	 * @return the num (0-65535)
	 */
	public static int getByteLittleEndian_unit16(byte[] theBytes,int idx)
	{
		int sum = 0;
		for (int i=0 ; i<2 ; i++)
		{
			sum = (sum<<8) + (0xff & theBytes[i+idx]);
		}
		return flip16(sum);
	}
	
	/**
	 * pull out unsigned 32 bits int out of the array.
	 * @param theBytes - the array
	 * @param idx - the starting index
	 * @return the num 
	 */
	static long getByteNetOrderTo_unit32(byte[] theBytes,int idx)
	{
		long sum = 0;
		for (int i=0 ; i<4 ; i++)
		{
			sum = sum*256 + (0xff & theBytes[i+idx]);
		}
		return sum;
	}
	
	/**
	 * Limited to max of 8 bytes long
	 * @param theBytes
	 * @param idx index
	 * @param size size
	 * @return signed long value.
	 */
	public static long getByteNetOrder(byte[] theBytes,int idx,int size)
	{
		long sum = 0;
		for (int i = 0; i < size; i++)
		{
			sum = sum * 256 + (0xff & theBytes[i+idx]);
		}
		return sum;
	}
		
	/**
	 * translate ip byte array to string
	 * @param theBytes - the byte array
	 * @param startIndex - the start idx
	 * @return the ip as string
	 * @throws UnknownHostException
	 */
	public static String getAsIpv4AsString(byte[] theBytes,int startIndex) throws UnknownHostException
	{
		if (theBytes == null || theBytes.length - startIndex > IPV4_ADDRESS_LEN)
			throw new UnknownHostException();
		StringBuilder toReturn = new StringBuilder();
		for (int i = 0; i < IPV4_ADDRESS_LEN; i++)
		{
			if (i!=0)
			{
				toReturn.append(".");
			}
			int field = (0xff & theBytes[i+startIndex]);
			toReturn.append(field);
		}
		return toReturn.toString();
	}
	
	/**
	 * turn ip in string representation to byte array in network order.
	 * @param ipAddress string type address
	 * @return ip as byte array
	 * @throws UnknownHostException
	 */
	public static byte[] getIPV4NetworkOrder(String ipAddress) throws UnknownHostException
	{
		byte[] toReturn = new byte[IPV4_ADDRESS_LEN];
		
		String[] fields = ipAddress.split("\\.");
		
		if (fields.length < IPV4_ADDRESS_LEN)
			throw new UnknownHostException();
		
		for (int i = 0; i < fields.length; i++) {
			toReturn[i] = (byte) Integer.parseInt(fields[i]);
		}
		return toReturn;
	}
	
	/**
	 * put number in array (big endian way)
	 * @param toPutIn - the array to put in
	 * @param startIndex - start index of the num
	 * @param theNumber - the number
	 * @param len - the number size in bytes.
	 */
	public static void setBigIndianInBytesArray(byte[] toPutIn,int startIndex,long theNumber,int len)
	{
		for(int i=0 ; i < len ; i++)
		{
			long num = (theNumber >> (8*(len - (i+1)))) & 0xff;
			toPutIn[i+startIndex] = (byte) num;
		}
	}
	
	/**
	 * put number in array (big endian way)
	 * @param toPutIn - the array to put in
	 * @param startIndex - start index of the num
	 * @param theNumber - the number
	 * @param len - the number size in bytes.
	 */
	public static void setLittleIndianInBytesArray(byte[] toPutIn,int startIndex,long theNumber,int len)
	{
		for(int i=0 ; i < len ; i++)
		{
			toPutIn[i+startIndex] = (byte) (theNumber % 256);
			theNumber/=256;
		}
	}
	
	/**
	 * copy byte array from array.
	 * @param from - the original array
	 * @param stratIndex - the start index
	 * @param ln - the length of the target array.
	 * @return - the slice copied from the original array
	 */
	public static byte[] extractBytesArray(byte[] from,int stratIndex,int ln)
	{
		byte[] toReturn = new byte[ln];
		System.arraycopy(from,stratIndex,toReturn,0,toReturn.length);
		return toReturn;
	}
		
	/**
	 * for switching big/small endian
	 * @param num target number will be changed
	 * @return flipped representation.
	 */
	public static long flip32(long num)
	{
		return ((num & 0x000000FF) << 24) + ((num & 0x0000FF00) << 8) + ((num & 0x00FF0000) >> 8) + ((num & 0xFF000000) >> 24);
	}

	/**
	 * for switching big/small endian
	 * @param num target number will be changed
	 * @return flipped representation.
	 */
	public static int flip16(int num)
	{
		return ((num & 0x00FF) << 8) + ((num & 0xFF00) >> 8);
	}

	/**
	 * The function change byte array order from little to big.
	 * @param data - the byte array to convert
	 * @return converted byte array (not done in place).
	 */
	public static byte[] convertLittleToBig(final byte[] data)
	{
		byte toRet[] = new byte[data.length];
		for(int i=0 ; i < data.length ; i++)
		{
			toRet[i] = data[data.length-i-1];
		}
		return toRet;
	}
}
