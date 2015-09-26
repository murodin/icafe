/**
 * Copyright (c) 2014-2015 by Wen Yu.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Any modifications to this file must keep this entire header intact.
 */

package com.icafe4j.image.reader;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.*;
import java.awt.image.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.icafe4j.image.compression.huffman.HuffmanTbl;
import com.icafe4j.image.jpeg.HTable;
import com.icafe4j.image.jpeg.QTable;
import com.icafe4j.io.IOUtils;

/** 
  * Java JPEG decoder based on the jpgvu.c Version 1.2 written by Elmo Ivey, 
  * Copyright (c) 1998, Integer Business Computer Systems.
  *
  * Current version is a baseline JFIF compatible one, doesn't support
  * progressive DCT format. JFIF extension segments are not supported!
  *
  * @author Wen Yu, yuwen_66@yahoo.com
  * @version 1.0 04/23/2007
  */
public class JPEGReaderEx extends ImageReader
{  
   public static final int LUMINANCE = 1;
   public static final int CHROMINANCE = 2;   
   /**
    * A segment is a stream of bytes with length <= 65535.The segment
    * begins with a marker. A marker consists of 2 bytes beginning with
    * 0xFF, and ending with a byte between 0 and 0xFF.
    */
   public static final int SEGMENT_IDENTIFIER = 0xff;
   public static final int END_OF_STREAM = 256;
   /**
    * Define JPEG markers. 
    * A marker is prefixed by a SEGMENT_IDENTIFIER. 
    * Most markers will have additional information following them. 
    * When this is the case, the marker and its associated information
    * is referred to as a "header." In a header the marker is immediately 
    * followed by two bytes that indicate the length of the information, 
    * in bytes, that the header contains. The two bytes that indicate the 
    * length are always included in that count.
    */
   public static final int TEM  = 0x01; // Usually causes a decoding error, may be ignored 
   public static final int SOF0 = 0xc0; // Baseline DCT process frame marker
   public static final int SOF1 = 0xc1; // Extended sequential DCT frame marker, Huffman coding
   public static final int SOF2 = 0xc2; // Progressive DCT frame marker, Huffman coding
   public static final int SOF3 = 0xc3; // Lossless process frame marker, Huffman coding
   public static final int DHT  = 0xc4; // Define Huffman table 
   public static final int SOF5 = 0xc5; // Differential sequential DCT frame marker, Huffman coding
   public static final int SOF6 = 0xc6; // Differential progressive DCT frame marker, Huffman coding
   public static final int SOF7 = 0xc7; // Differential lossless process frame marker, Huffman coding
   public static final int JPG  = 0xc8; // Undefined/reserved (causes decoding error) 
   public static final int SOF9 = 0xc9; // Sequential DCT frame marker, arithmetic coding
   public static final int SOF10= 0xca; // Progressive DCT frame marker, arithmetic coding
   public static final int SOF11= 0xcb; // Lossless process frame marker, arithmetic coding
   public static final int DAC  = 0xcc; // Define Arithmetic Table, usually unsupported 
   public static final int SOF13= 0xcd; // Differential sequential DCT frame marker, arithmetic coding
   public static final int SOF14= 0xce; // Differential progressive DCT frame marker, arithmetic coding
   public static final int SOF15= 0xcf; // Differential lossless process frame marker, arithmetic coding
   /**
    * RSTn are used for resync, may be ignored, no length and other contents are
    * associated with these markers. 
    */
   public static final int RST0 = 0xd0;  
   public static final int RST1 = 0xd1;
   public static final int RST2 = 0xd2;
   public static final int RST3 = 0xd3;
   public static final int RST4 = 0xd4;
   public static final int RST5 = 0xd5;
   public static final int RST6 = 0xd6;
   public static final int RST7 = 0xd7;
   //End of RSTn definitions
   public static final int SOI  = 0xd8; // Start of image 
   public static final int EOI  = 0xd9; // End of image, the very last marker 
   public static final int SOS  = 0xda; // Start of scan
   public static final int DQT  = 0xdb; // Define Quantization table
   public static final int DNL  = 0xdc; // Define number of lines
   public static final int DRI  = 0xdd; // Define Restart Interval
   public static final int DHP  = 0xde; // Define hierarchical progression
   public static final int EXP  = 0xdf; // Expand reference components
   public static final int APP0 = 0xe0; // JFIF application segment
   public static final int APP1 = 0xe1; // EXIF application segment
   public static final int APP2 = 0xe2; // FPXR or ICC Profile data 
   public static final int APP3 = 0xe3; 
   public static final int APP4 = 0xe4; 
   public static final int APP5 = 0xe5; 
   public static final int APP6 = 0xe6; 
   public static final int APP7 = 0xe7; 
   public static final int APP8 = 0xe8; 
   public static final int APP9 = 0xe9; 
   public static final int APP10 =0xea; 
   public static final int APP11 =0xeb; 
   public static final int APP12 =0xec; 
   public static final int APP13 =0xed;// IPTC and Photoshop data 
   public static final int APP14 =0xee; 
   public static final int APP15= 0xef; 
   public static final int JPG0 = 0xf0; // Reserved
   // A lot more here      ...
   public static final int JPG13= 0xfd; // Reserved
   public static final int COM  = 0xfe; // Comment marker
   // End of JPEG marker definitions
   // For JFIF there are normally two quantization tables, but for
   // other format there can be up to 4 quantization tables!
   private int quant_tbl[][] = new int[4][64];
   private int transform[][] = new int[8][8];
   private int block[] = new int[64];
   private int zigzag_array[] = new int[64];

   boolean finished = false;
   boolean reset_mcu = false;

   private int image_height = 0;
   private int image_width = 0;

   private int max_v_samp_factor = 0;
   private int max_h_samp_factor = 0;

   private HuffmanTbl dc_huftbl[] = new HuffmanTbl[4];
   private HuffmanTbl ac_huftbl[] = new HuffmanTbl[4];

   private int component[][] = new int[4][4];
   private int scan_component[][] = new int[4][3];

   @SuppressWarnings("unused")
   private HUF_NODE[][] dc_node;
   @SuppressWarnings("unused")
   private HUF_NODE[][] ac_node;

   @SuppressWarnings("unused")
   private int total_nodes;
   @SuppressWarnings("unused")
   private int[] dc_total_nodes;
   @SuppressWarnings("unused")
   private int[] ac_total_nodes;

   Image img_thumb;
   private boolean thumbnail = false;
   private boolean firstAPP0 = true;
   
   /**
    * Used to keep track of the tables when reading SOS segment.
    * Quantization and Huffman tables are comparable and can be sorted after
    * all the table segments are read. 
    */   
   @SuppressWarnings("unused")
   private List<QTable> qTables = new ArrayList<QTable>(4);
   @SuppressWarnings("unused")
   private List<HTable> acTables = new ArrayList<HTable>(4);
   @SuppressWarnings("unused")
   private List<HTable> dcTables = new ArrayList<HTable>(4);
   
   // Obtain a logger instance
   private static final Logger LOGGER = LoggerFactory.getLogger(JPEGReaderEx.class);

   public BufferedImage read(InputStream is) throws Exception
   {
        finished = false;
		int identifier = -1;
		int marker = 0;

		identifier = is.read();// Read segment identifier
            
		if (identifier == -1)
		{
			LOGGER.error("END OF FILE!");
			return null;
		}
		// The very first marker should be the start_of_image marker!	
		if((identifier != SEGMENT_IDENTIFIER)||((marker = is.read()) != SOI))
		{
			LOGGER.error("Invalid JPEG image!");
			return null;
		}
		LOGGER.info("SOI(0x{})", Integer.toHexString(SOI));
	
		while (!finished)
        {
		    identifier = is.read(); // Keep reading segment identifiers
            
			if (identifier == -1)
			{
				LOGGER.info("END OF FILE!");
				finished = true;
			}
			
			else if(identifier != SEGMENT_IDENTIFIER)
			{
				LOGGER.error("Invalid SEGMENT_IDENTIFIER!");
				finished  = true;
				//continue;
			}
	    	else
			{
				marker = is.read(); // Read marker

				while(marker == SEGMENT_IDENTIFIER)
				{
					marker = is.read();
				}
				if(marker==0x00) continue;// This is not a marker
               // RSTs, there is no length byte and other contents
               // associated with these markers, skip...
                if ((marker >= 0xd0)&&(marker <= 0xd7))  
                { ; }                              
                else if ((marker >= APP0)&&(marker <= APP15))
                	read_Application_Segment(marker,is);
               	else
				{
				   switch (marker)
				   {
    			         case SEGMENT_IDENTIFIER: break;
						 case DQT:
				         {
					        read_DQT_Segment(is);
					        break;
				         }
				         case DHT:
				         {
					        read_DHT_Segment(is);
					        break;
				         }
				         case SOF0:
				         case SOF2:
				         {
				        	 read_SOF_Segment(is, marker);
				        	 break;				        	 
				         }
				         case SOS:
				         {
					        read_SOS_Segment(is);
					        break;
				         }
			             case DRI:
		                 {
			                read_DRI_Segment(is);
			                break;
		                 }
				         case EOI:
						 case  -1:
				         {
					        read_EOI_Segment();
					        break;
				         }
						 case COM:
				         {
					        read_COM_Segment(is);
					        break;
				         }
				         default:
				         {
					        read_Unknown_Segment(is, marker, read_word(is));
					        break;
				         }
				   }// End of switch
				}
			}
        }
		
		return null;
   }
   
   // Process JFIF APPn header segment
   public boolean read_Application_Segment(int APPn,InputStream is) throws IOException
   {
	   int len=read_word(is);// Length of the JFIF header

	   switch(APPn)
	   { 
		    case APP0:
		    {   
				if (firstAPP0)
		        {
					byte[] identifier=new byte[5];
					IOUtils.readFully(is,identifier,0,5);
					String JFIF_IDENTIFIER=new String(identifier); // JFIF identifier
					if (!JFIF_IDENTIFIER.trim().equals("JFIF"))
					{
						LOGGER.error("NOT a valid JFIF file");
						finished = true;
						return false;
					}
					int major_revision = is.read();
					int minor_revision = is.read();
					// Printout JFIF and revision information, the major_revision should be 1
					// and the minor_revision should be 0..2, otherwise try to decode anyway            
					LOGGER.info("APP0(0x{}): {}{}.{}", Integer.toHexString(APP0), JFIF_IDENTIFIER, major_revision, minor_revision); 
					IOUtils.skipFully(is,5); //x/y densities ignored
					// Thumbnail follows
					int thumb_width = is.read();
					LOGGER.info("thumbnail width is: {}", thumb_width);
					int thumb_height = is.read();
					LOGGER.info("thumbnail height is: {}", thumb_height);
					if((thumb_width!=0)&&(thumb_height!=0))
					{
					  thumbnail = true;
					  generate_thumbnail(thumb_width,thumb_height,is);
					}
					firstAPP0 = false;
		        }
				else
				{
					read_Unknown_Segment(is, APP0, len);
				}
				break;
		    }
			case APP1:
			{
               /** 
                 * This format is specific to digital cameras
				 * to insert information about camera setups to 
				 * the JPEG images,the specification is made by 
				 * Japanese producer.
				 * sometimes, there are more than one app1 segments found,
				 * this will cause some confusion, but it won't stop the decoder!
				 */
				byte[] buf = new byte[len-2];
				IOUtils.readFully(is,buf,0,len-2);
				int identifier_len = 0;
				while ((buf[identifier_len]&0xff)!=0)
				{
					identifier_len++;
				}
				// Exif header
		        //byte[] identifier=new byte[6];
				//is.read(identifier,0,6);
				String IDENTIFIER=new String(buf,0,identifier_len,"UTF-8"); // Could be Exif or Adobe XMP
				LOGGER.info("APP1(0x{}): [{}]", Integer.toHexString(APP1), IDENTIFIER);
				break;
			}
			case APP2:
			{
				// FPXR or ICC Profile data
				byte[] buf = new byte[len-2];
				IOUtils.readFully(is,buf,0,len-2);
				int identifier_len = 0;
				while ((buf[identifier_len]&0xff)!=0)
				{
					identifier_len++;
				}
				String IDENTIFIER=new String(buf,0,identifier_len,"UTF-8");
				LOGGER.info("APP2(0x{}): [{}]", Integer.toHexString(APP2), IDENTIFIER);
				break;
			}
			case APP12: // [Ducky]
			{
				// Some digital cameras store useful text information in APP12 markers.
				byte[] buf = new byte[len-2];
				IOUtils.readFully(is,buf,0,len-2);
				int identifier_len = 0;
				while ((buf[identifier_len]&0xff)!=0)
				{
					identifier_len++;
				}
				String IDENTIFIER=new String(buf,0,identifier_len,"UTF-8");
				LOGGER.info("APP12(0x{}): [{}]", Integer.toHexString(APP12), IDENTIFIER);
				break;
			}
			case APP13:// [Photoshop 3.0]
			{	
				// IPTC and Photoshop data
			    byte[] buf = new byte[len-2];
				IOUtils.readFully(is,buf,0,len-2);
				int identifier_len = 0;
				while ((buf[identifier_len]&0xff)!=0)
				{
					identifier_len++;
				}
				String IDENTIFIER=new String(buf,0,identifier_len,"UTF-8");
				LOGGER.info("APP13(0x{}): [{}]", Integer.toHexString(APP13), IDENTIFIER);
				break;				
			}
			case APP14:// [Adobe]
			{
				byte[] buf = new byte[len-2];
				IOUtils.readFully(is,buf,0,len-2);
				int identifier_len = 0;
				while ((buf[identifier_len]&0xff)!=0)
				{
					identifier_len++;
				}
				String IDENTIFIER=new String(buf,0,identifier_len,"UTF-8");
				LOGGER.info("APP14(0x{}): [{}]", Integer.toHexString(APP14), IDENTIFIER);
				break;
			}
			case APP15:
			{
				byte[] buf = new byte[len-2];
				IOUtils.readFully(is,buf,0,len-2);
				int identifier_len = 0;
				while ((buf[identifier_len]&0xff)!=0)
				{
					identifier_len++;
				}
				String IDENTIFIER=new String(buf,0,identifier_len,"UTF-8");
				LOGGER.info("APP15(0x{}): [{}]", Integer.toHexString(APP15), IDENTIFIER);
				break;
			}
			default:
			    read_Unknown_Segment(is, APPn, len);
	    }// End of switch!	
	   
	   return true;
   }
    
   public boolean containsThumbnail()
   {
	   return thumbnail;
   }
   
   // Generate thumbnail view
   private void generate_thumbnail(int thumb_width,int thumb_height,InputStream is) throws IOException
   {
	    int pix_thumb[]=new int[thumb_width*thumb_height];
		int index=0;
		int nindex=0;
		byte brgb[]=new byte[3*thumb_width];
		for(int i=1;i<=thumb_height;i++)
		{
    		  IOUtils.readFully(is,brgb,0,3*thumb_width);
			  for(int j=0;j<thumb_width;j++)
		  	  {
			      pix_thumb[index++]=((0xff<<24)|((brgb[nindex++]&0xff)<<16))|((brgb[nindex++]&0xff)<<8)|(brgb[nindex++]&0xff);
			  }
	    }
	    img_thumb = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(thumb_width, thumb_height, pix_thumb, 0, thumb_width));
		LOGGER.info("Thumbnail view created!");
   }
   
   // Process define Quantization table
   public void read_DQT_Segment(InputStream is) throws IOException
   {
		// Define quantization table segment, only for 8 bits precision
        LOGGER.info("DQT(0x{})", Integer.toHexString(DQT));
		int QT_info = 0;
		int QT_precision = 0;
        int QT_index = 0;
		byte buf[] = new byte[64];;
        int count = 0;
        
		int len = read_word(is);
		len -= 2;//
		  
		while(len > 0)
		{
			QT_info = is.read();
		    QT_precision = (QT_info>>4)&0x0f;
		    //LOGGER.info("precision of QT is {}", QT_precision);
			if (QT_precision != 0)
			{
				LOGGER.error("only 8 bit precision is surported!");
			    finished = true;
			    return;
			}
			QT_index = (QT_info&0x0f);
		    //LOGGER.info("for QT_no {}", QT_index);
		    // Read QT tables
    	    // We assume a precision value of zero
			IOUtils.readFully(is, buf,0,64);
			for (int j=0; j<64; j++ )
		    {
				quant_tbl[QT_index][j] = buf[j]&0xff;
		    }
			len -= 65;
			count ++;
		}
		LOGGER.info("number of Quantation tables: {}", count);
   }
   
   // Process define Huffman table
   public void read_DHT_Segment(InputStream is) throws IOException
   {
		// Define huffman table segment
		int HT_info=0;
        int HT_index=0;
		int HT_type =0;
  		int count=0;
		byte bits[];
		byte values[];
	
		LOGGER.info("DHT(0x{})", Integer.toHexString(DHT));
        int len=read_word(is);
		len -= 2;
		while (len > 0)
		{
			HT_info=is.read();
			HT_type=(HT_info>>4)&0x0f;// 0=DC table, 1=AC table
			HT_index=(HT_info&0x0f);// Huffman tables number
            //LOGGER.info("HT type is {}", HT_type);
			//LOGGER.info("HT_num is {}", HT_index);
			count = 0;			
			bits = new byte[16];
            // Number of Huffman code of length i. (i=1..16)
            IOUtils.readFully(is,bits,0,16);

			for (int i=0; i<16; i++ )
			{
				count += bits[i]&0xff;
			}
            //LOGGER.info("number of Huffman code: {}", count);
			if (count>256)
			{
				LOGGER.error("invalid huffman code count!");
				finished = true;
				return;
			}
			
			values = new byte[count];
          	IOUtils.readFully(is,values,0,count);
          	
			if (HT_type == 0)
            {
				dc_huftbl[HT_index] = new HuffmanTbl(bits, values);
            }
			else 
			{
				ac_huftbl[HT_index] = new HuffmanTbl(bits, values);
            }
			
			len -= (1+16+count);
		}
   }
   
   // Process SOF segment
   public void read_SOF_Segment(InputStream is,int SOFn) throws IOException
   {
	    LOGGER.info("SOF(0x{})", Integer.toHexString(SOFn));
		int samp_factor = 0;
	    int len = read_word(is);
		// This is in bits/sample, usually 8, (12 and 16 not supported by most software). 
		int precision = is.read();// Usually 8, for baseline JPEG
		if (precision != 8)
		{
			LOGGER.error("only 8 bit precision is surported!");
			finished = true;
			return;
		}
		// Image width and height
		image_height = read_word(is);
		image_width = read_word(is);
		LOGGER.info("image size: {}X{}", image_width, image_height);
        // Number of components
		// Usually 1 = grey scaled, 3 = color YCbCr or YIQ, 4 = color CMYK 
        // JFIF uses either 1 component (Y, greyscaled) or 3 components (YCbCr, sometimes called YUV, color).
		int num_of_components = is.read();
		//LOGGER.info("number of components: {}", num_components);
		for (int i=0; i<num_of_components; i++)
		{
			// Component ID(1 byte)(1 = Y, 2 = Cb, 3 = Cr, 4 = I, 5 = Q)    
			component[i][0] = is.read();//Component id
			// Sampling factors (1byte) (bit 0-3 horizontal, 4-7 vertical).
			// This is to be confirmed!
			samp_factor = is.read();
			component[i][1] = (samp_factor>>4)&0x0f;//Horizontal 
			component[i][2] = (samp_factor&0x0f);//Vertical
            if (component[i][1] > max_h_samp_factor)
            {
				   max_h_samp_factor = component[i][1];
            }
			if (component[i][2] > max_v_samp_factor)
			{
				   max_v_samp_factor = component[i][2];
			}
			component[i][3] = is.read();// Quantization table number
			//LOGGER.info("component {} quantization table number: {}", component[i][0], component[i][3]);
		}
		IOUtils.skipFully(is,len-8-3*num_of_components);
		
	    if(SOFn == SOF2)
	    {
	    	LOGGER.error("progressive DCT, Huffman coding not supported!");
		    finished = true;
		    return;		
		}	    
   }
   
   // Process start of scan 
   public void read_SOS_Segment(InputStream is) throws IOException
   {
		// Start of scan segment
		LOGGER.info("SOS(0x{})", Integer.toHexString(SOS));
		
		int tbl_no = 0;
        int Ss, Se, Ah_Al, Ah, Al;

		int len = read_word(is);
		LOGGER.info("length is: {}", len);

		// The number of components in this scan
		int num_of_components = is.read();
		LOGGER.info("number of components in this scan: {}", num_of_components);
		len -= 3;

		for (int i=0; i<num_of_components; i++)
		{
			scan_component[i][0] = is.read();// Component id
			tbl_no = is.read();
			scan_component[i][1] = tbl_no&0x0f;// DC table number
			scan_component[i][2] = (tbl_no>>4)&0x0f;// AC table number
			len -= 2;
		}
		Ss = is.read();// Start of spectral or predictor selection
		Se = is.read();// End of spectral selection		
		LOGGER.info("Ss: {}", Ss);
		LOGGER.info("Se: {}", Se);
		//LOGGER.info("Ss and Se are: {}{}{}", Ss, "&", Se);
		Ah_Al = is.read();
        Ah = (Ah_Al>>4)&0x0f;// Successive approximation bit position high
		Al = Ah_Al&0x0f;// Successive approximation bit position low or point transform
		LOGGER.info("Ah: {}", Ah);
		LOGGER.info("Al: {}", Al);
		IOUtils.skipFully(is,len-3);
		LOGGER.info("length remains: {}", (len-3));
 		// Begin of the image data
		decode_image_data(is);
   }
   
   // Process define restart interval
   public void read_DRI_Segment(InputStream is) throws IOException
   {
	   LOGGER.info("DRI(0x{})", Integer.toHexString(DRI));
	   IOUtils.skipFully(is,read_word(is)-2);
   }
   
   // Process unknown marker
   public void read_Unknown_Segment(InputStream is, int marker, int len) throws IOException
   {
	   byte[] buf = new byte[len-2];
	   IOUtils.readFully(is,buf,0,len-2);
	   int identifier_len = 0;
	   while ((buf[identifier_len]&0xff)!=0)
	   {
		  identifier_len++;
	   }
	   String IDENTIFIER=new String(buf,0,identifier_len,"UTF-8");	  	   
	   if ((marker>=0xe0)&&(marker<=0xef))
	   {
		  LOGGER.info("Application type marker: 0x{}", Integer.toHexString(marker));
	   }
	   else
	   {
		  LOGGER.warn("Unrecognized marker: 0x{}", Integer.toHexString(marker));
	   }
	   LOGGER.info("with identifier: [{}] ignored!", IDENTIFIER);
   }
   
   // Process comment marker
   public void read_COM_Segment(InputStream is) throws IOException
   {
	   int len=read_word(is);
	   byte buf[]=new byte[len-2];
	   IOUtils.readFully(is,buf,0,len-2);
	   LOGGER.info("Comment: {}", new String(buf,"UTF-8"));
   }
   
   // Process end of image marker
   public void read_EOI_Segment()
   {
	   LOGGER.info("END OF IMAGE!");
	   finished = true;
   }

   /**
    * @param is  
    */
   public void decode_image_data(InputStream is)
   {
	   LOGGER.info("Start Processing Image Data...");
   }

   @SuppressWarnings("unused")
   private byte getbit()
   {
	   return 0;
   }

   @SuppressWarnings("unused")
   private void build_transform()
   {
	   int x,y,k=0;
	   for (y=0;y<8;y++)
	     for (x=0;x<8;x++)
	     {
		   transform[x][y]=block[k];
		   k++;		    
	     }
   }
   
   // Shift a signed 8*8 block to unsigned one 
   @SuppressWarnings("unused")
   private void shift_block(int k,int l,int m)
   {
	   int x,y;

	   for (x=0;x<8;x++)
	   	   for (y=0;y<8;y++)
		     ;// component[l][m][k][x][y]+=128;
   }

   @SuppressWarnings("unused")
   private void idct(int k,int l,int m)
   {
	   int x,y;

	   for (x=0;x<8;x++)
	     for (y=0;y<8;y++)
	       ;// component[l][m][k][x][y]=f(x,y);
   }

   @SuppressWarnings("unused")
   private int f(int x,int y)
   {
	   int u,v;
	   float sum=0;

	   for (u=0;u<8;u++)
	     for (v=0;v<8;v++)
	       sum+=(C(u)*C(v))*transform[u][v]*(Math.cos(((2*x+1)*u*Math.PI)/16))*Math.cos(((2*y+1)*v*Math.PI)/16);
	   return (int)((1.0/4.0)*sum);
   }

   private float C(int u)
   {
	   if (u==0) return (float)(1.0/Math.sqrt(2));
	   return 1.0f;
   }

   @SuppressWarnings("unused")
   private int YCbCr_to_RGB(int Y,int Cb,int Cr)
   {
	   float red,green,blue;

	   red = Y + 1.402f*(Cr-128);
	   if (red<0) red = 0;
	   if (red>255) red = 255;
	   
	   green = Y - 0.34414f*(Cb-128)-0.71414f*(Cr-128);
	   if (green<0) green = 0;
	   if (green>255) green = 255;

	   blue= Y + 1.772f*(Cb-128);
	   if (blue<0) blue = 0;
	   if (blue>255) blue = 255; 

	   return ((0xff<<24)|((int)red<<16)|((int)green<<8)|(int)blue);
   }

   @SuppressWarnings("unused")
   private void setup_zigzag_array()
   {
	   int i=0,j=0,x=0,y=0,k=0;
	   
	   for (j=0;j<8;j++)
	   {
		   for (i=0;i<=j;i++)
		   {
			   if (j%2==0)
			   {
				   x=i;
				   y=j-1;
			   }
			   else
			   {
				   x=j-i;
				   y=i;
			   }
			   zigzag_array[k]=(y<<3)+x;
			   k++;
		   }
	   }

	   for (j=1;j<8;j++)
	   {
		   for (i=0;i<8-1-j;i++)
		   {
			   if (j%2==0)
			   {
				   x=8-1-i;
				   y=j+i;
			   }
			   else
			   {
				   x=j+i;
				   y=8-1-i; 
			   }
			   zigzag_array[k]=(y<<3)+x;
			   k++;
		   }
	   }
   }
	
   private int read_word(InputStream is) throws IOException
   {
	   return (is.read()<<8)|(is.read());
   }
  
   class HUF_NODE
   {
	   int code;
	   int size;
	   int value;
	   int tree_index;
   }
}