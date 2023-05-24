package prev23.phase.all;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import prev23.data.asm.*;
import prev23.data.lin.LinDataChunk;
import prev23.phase.*;
import prev23.phase.asmgen.*;
import prev23.phase.imclin.*;
import prev23.phase.regall.RegAll;
import prev23.Compiler;

public class LastPhase extends Phase {
    public final long SP_init = 0x7FFFFFFFFFFFFFF8l;
	public final long HP_init = 0x2000000000000000l;

	public LinkedList<String> asm;

	public LastPhase() {
		super("all");
		asm = new LinkedList<>();
	}

    public void saveNumber(long number, int register) {
		asm.add("\t\t\tSETL $" + register + "," + Long.toString(number & 0x000000000000FFFFL));

		if((number & 0x00000000FFFF0000L) > 0)
			asm.add("\t\t\tINCML $" + register + "," + Long.toString((number & 0x00000000FFFF0000L) >> 16));
		if((number & 0x0000FFFF00000000L) > 0)
			asm.add("\t\t\tINCMH $" + register + "," + Long.toString((number & 0x0000FFFF00000000L) >> 32));
		if((number & 0xFFFF000000000000L) > 0)
			asm.add("\t\t\tINCH $" + register + "," + Long.toString((number & 0xFFFF000000000000L) >> 48));
	}

    public void prepare() {
        // prepare everything then call main
        addStart();

        // loop through all the codes
        // add the prologue, the code and the epilogue
        for (Code code : AsmGen.codes) {
			addPrologue(code);
			
			asm.add("# " + code.frame.label.name + " - Body");
			for(int i = 1; i < code.instrs.size(); i++) {
				AsmInstr prev = code.instrs.get(i - 1);
				AsmInstr curr = code.instrs.get(i);
				String line = "";

				// to get labels inline we have to do some magic
				if(prev instanceof AsmLABEL) {
					line = ((AsmLABEL) prev).getLabel().name;
					if (curr instanceof AsmLABEL) {
						line += "\t\t\tSWYM";
					}
					else {
						line += "\t\t\t" + curr.toString(RegAll.tempToReg);
					}
					asm.add(line);
				}
				else {
					if(curr instanceof AsmLABEL) {
						continue;
					}
					line = curr.toString(RegAll.tempToReg);
					asm.add("\t\t\t" + line);
				}
			}

			addEpilogue(code);
		}

        // add the standard library
        asm.add("\n# Standard library functions");
        addStandardLibrary();
    }

    public void addStart() {
        // setup global registers
		asm.add("\t\t\tGREG");	// SP $254
		asm.add("\t\t\tGREG");	// FP $253
		asm.add("\t\t\tGREG");	// HP $252

		// setup buffers
		asm.add("\t\t\tLOC Data_Segment");
		asm.add("\t\t\tGREG @");
		asm.add("InBuffer\tOCTA\t0");
		asm.add("OutBuffer\tOCTA\t0");
		asm.add("InArgs\t\tOCTA\tInBuffer,#2");

        // add global variables
        if(ImcLin.dataChunks().size() > 0)
            asm.add("# Global variables");
        for (LinDataChunk data : ImcLin.dataChunks()) {
            // System.out.println(data.label.name + ", " + data.size);
            if(data.init == null) {
                asm.add(data.label.name + "\t\tOCTA\t0");
                if(data.size > 8) // pesky arrays
                    asm.add("\t\t\tLOC\t@+" + (data.size - 8));
            }
            // string
            else {
                String value = data.label.name + "\t\t\tOCTA\t\"";
                String temp = data.init;
                if(temp.contains("\"")) {	// for handling quotes
                    temp = temp.replaceAll("\"", "\",34,\"");
                    if(temp.endsWith(",34,\""))
                        temp = temp.substring(0, temp.length() - 4);
                }
                
                value += temp;
                asm.add(value + "\",0");
            }
        }

		// setup location
		asm.add("\t\t\tLOC #100");
		asm.add("Main\t\tSWYM");

		// setup global registers init values
		saveNumber(SP_init, 254);
		saveNumber(HP_init, 252);

		// call main
		asm.add("\t\t\tPUSHJ $" + RegAll.NUM_REGISTERS + ",_main"); 
        asm.add("\t\t\tLDO $255,$254,0");	// put the return value in the $255 register
		asm.add("\t\t\tTRAP 0,Halt,0");
    }

    public void addPrologue(Code code) {
        asm.add("\n# " + code.frame.label.name + " - Prologue");
		asm.add(code.frame.label.name + "\t\tSWYM");

		// adjust fp and sp
		long oldFP = code.frame.locsSize + 8;
		saveNumber(oldFP, 0);

		asm.add("\t\t\tSUB $254,$254,$0");	// SP -= oldFP
		asm.add("\t\t\tSTO $253,$254,0"); 	// store the current FP at this location
		asm.add("\t\t\tSUB $254,$254,8"); 	// go one octa futher down (RA)
		asm.add("\t\t\tGET $253,rJ");  		// store the return address in FP (RA)
		asm.add("\t\t\tSTO $253,$254,0");	// save ret address
		
		// restore SP
		asm.add("\t\t\tADD $254,$254,8");	// go one octa back up (RA)
		asm.add("\t\t\tADD $254,$254,$0");	// SP += oldFP
		asm.add("\t\t\tOR $253,$254,0");	// FP = SP

		// Move FP, SP
		saveNumber(code.frame.size, 1);		// save framesize to $1
		asm.add("\t\t\tSUB $254,$254,$1");	  // SP -= framesize
		
		// Jump
		asm.add("\t\t\tJMP " + code.entryLabel.name);
    }

    public void addEpilogue(Code code) {
		asm.add("# " + code.frame.label.name + " - Epilogue");

		// return value
		asm.add(code.exitLabel.name + "\t\t\tSTO $" + RegAll.tempToReg.get(code.frame.RV) + ",$253,0");
		asm.add("\t\t\tOR $254,$253,0");	// SP = FP

		long oldFP = code.frame.locsSize + 8;
		saveNumber(oldFP, 0);

		asm.add("\t\t\tSUB $0,$253,$0"); 	// old FP address
		asm.add("\t\t\tSUB $1,$0,8"); 		// return address
		asm.add("\t\t\tLDO $1,$1,0"); 		// return value
		asm.add("\t\t\tPUT rJ,$1");


		asm.add("\t\t\tLDO $253,$0,0");		// set new FP
		asm.add("\t\t\tPOP 0,0");
    }

    public void addStandardLibrary() {
        functionGetChar();
        functionPutChar();
        functionNew();
        functionDelete();
    }

    public void functionGetChar() {
		asm.add("_getChar\tLDA $255,InArgs");
		asm.add("\t\t\tTRAP 0,Fgets,StdIn");
		asm.add("\t\t\tLDA $1,InBuffer");
		asm.add("\t\t\tLDB $0,$1,0");
		asm.add("\t\t\tSTO $0,$254,0");
		asm.add("\t\t\tPOP 0,0");
	}

	public void functionPutChar() {
		asm.add("_putChar\tLDA $255,OutBuffer");
		asm.add("\t\t\tLDO $0,$254,8");
		asm.add("\t\t\tSTB $0,$255,0");
		asm.add("\t\t\tTRAP 0,Fputs,StdOut");
		asm.add("\t\t\tSTO $255,$254,0");
		asm.add("\t\t\tPOP 0,0");
	}

	public void functionNew() {
		asm.add("_new\t\tLDO $0,$254,8");
		asm.add("\t\t\tSTO $252,$254,0");
		asm.add("\t\t\tADD $252,$252,0");
		asm.add("\t\t\tPOP 0,0");
	}

	public void functionDelete() {
		asm.add("_del\t\tPOP 0,0");
	}

    public void exportToFile() {
        String filename = Compiler.cmdLineArgValue("--dst-file-name");
		System.out.println("Exporting to file: " + filename);
		try {
			FileWriter writer = new FileWriter(filename);

			for(String line : asm) {
				writer.write(line + "\n");
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    public void log() {
		if(logger == null)
			return;

		for (Code code : AsmGen.codes) {
			logger.begElement("code");
			logger.addAttribute("entrylabel", code.entryLabel.name);
			logger.addAttribute("exitlabel", code.exitLabel.name);
			logger.addAttribute("tempsize", Long.toString(code.tempSize));

			if(code.frame != null)
				code.frame.log(logger);
			
			logger.begElement("instructions");
			for(AsmInstr instr : code.instrs) {
				logger.begElement("instruction");
				logger.addAttribute("code", instr.toString(RegAll.tempToReg));
				logger.endElement();
			}

			logger.endElement();
			logger.endElement();
		}
	}

}
