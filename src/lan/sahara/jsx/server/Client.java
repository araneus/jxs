package lan.sahara.jsx.server;

import java.io.IOException;
import java.net.Socket;

import lan.sahara.jsx.interfaces.ClientApiInterface;

public class Client extends Thread {
    public static final int         Destroy = 0;
    public static final int         RetainPermanent = 1;
    public static final int         RetainTemporary = 2;

    private final ClientApiInterface		_outClient;
    private final XServer                   _xServer;
    private final Socket                    _socket;
    private final InputOutput               _inputOutput;
    private final int                               _resourceIdBase;
    private final int                               _resourceIdMask;
//    private final Vector<Resource>  _resources;
    private int                                             _sequenceNumber = 0;
    private boolean                                 _closeConnection = false;
    private boolean                                 _isConnected = true;
    private int                                             _closeDownMode = Destroy;	
	
	public Client(ClientApiInterface outClient,XServer xserver,Socket socket,int resourceIdBase,int resourceIdMask)  throws IOException {
		System.err.println("Client thread created");
		_outClient = outClient;
        _xServer = xserver;
        _socket = socket;
        _inputOutput = new InputOutput (socket);
        _resourceIdBase = resourceIdBase;
        _resourceIdMask = resourceIdMask;
//        _resources = new Vector<Resource>();
	}

	@Override
	public void run() {
		System.err.println("Client thread Started");
		try {
			doComms ();
		} catch (IOException e) {
		}

		synchronized (_xServer) {
			close ();
		}
	}

    /**
     * Close the communications thread and free resources.
     */
    private void close () {
    	if (!_isConnected)
    		return;

    	_isConnected = false;

    	try {
//			_inputOutput.close ();
    		_socket.close ();
    	} catch (IOException e) {
    	}

    	// Clear the resources associated with this client.
/*            
            if (_closeDownMode == Destroy) {
            	for (Resource r: _resources)
            		r.delete ();
            }
             _resources.clear ();
*/ 
 
    	_xServer.removeClient (this);
    }	
	
    /**
     * Handle communications with the client.
     * @throws IOException
     */
    private void doComms () throws IOException {
    	// Read the connection setup.
    	int byteOrder = _inputOutput.readByte ();
    	if (byteOrder == 0x42)
    		_inputOutput.setMSB (true);
    	else if (byteOrder == 0x6c)
    		_inputOutput.setMSB (false);
    	else
    		return;
    	
    	_inputOutput.readByte ();       // Unused.
    	_inputOutput.readShort ();      // Protocol major version.
    	_inputOutput.readShort ();      // Protocol minor version.

    	int             nameLength = _inputOutput.readShort ();
    	int             dataLength = _inputOutput.readShort ();

    	_inputOutput.readShort ();      // Unused.

    	if (nameLength > 0) {
    		_inputOutput.readSkip (nameLength);     // Authorization protocol name.
    		_inputOutput.readSkip (-nameLength & 3);        // Padding.
    	}

    	if (dataLength > 0) {
    		_inputOutput.readSkip (dataLength);     // Authorization protocol data.
    		_inputOutput.readSkip (-dataLength & 3);        // Padding.
    	}

    	// Complete the setup.
    	final byte[]    vendor = _xServer.vendor.getBytes ();
    	int                             pad = -vendor.length & 3;
    	int                             extra = 26 + 2 * _xServer.getNumFormats () + (vendor.length + pad) / 4;
//    	Keyboard                kb = _xServer.getKeyboard ();

    	synchronized (_inputOutput) {
                    _inputOutput.writeByte ((byte) 1);              // Success.
                    _inputOutput.writeByte ((byte) 0);              // Unused.
                    _inputOutput.writeShort (_xServer.ProtocolMajorVersion);
                    _inputOutput.writeShort (_xServer.ProtocolMinorVersion);
                    _inputOutput.writeShort ((short) extra);        // Length of data.
                    _inputOutput.writeInt (_xServer.ReleaseNumber); // Release number.
                    _inputOutput.writeInt (_resourceIdBase);
                    _inputOutput.writeInt (_resourceIdMask);
                    _inputOutput.writeInt (0);              // Motion buffer size.
                    _inputOutput.writeShort ((short) vendor.length);        // Vendor length.
                    _inputOutput.writeShort ((short) 0x7fff);       // Max request length.
                    _inputOutput.writeByte ((byte) 1);      // Number of screens.
                    _inputOutput.writeByte ((byte) _xServer.getNumFormats ());
                    _inputOutput.writeByte ((byte) 0);      // Image byte order (0=LSB, 1=MSB).
                    _inputOutput.writeByte ((byte) 1);      // Bitmap bit order (0=LSB, 1=MSB).
                    _inputOutput.writeByte ((byte) 8);      // Bitmap format scanline unit.
                    _inputOutput.writeByte ((byte) 8);      // Bitmap format scanline pad.
                    _inputOutput.writeByte ((byte) 8);		// min key code
                    _inputOutput.writeByte ((byte) 255); 	// max key code
//                    _inputOutput.writeByte ((byte) kb.getMinimumKeycode ());
//                    _inputOutput.writeByte ((byte) kb.getMaximumKeycode ());
                    _inputOutput.writePadBytes (4); // Unused.

                    if (vendor.length > 0) {        // Write padded vendor string.
                            _inputOutput.writeBytes (vendor, 0, vendor.length);
                            _inputOutput.writePadBytes (pad);
                    }

                    _xServer.writeFormats (_inputOutput);
                    _xServer.getScreen().write (_inputOutput);
            }
            _inputOutput.flush ();

            while (!_closeConnection) {
            	byte    opcode = (byte) _inputOutput.readByte ();
            	byte    arg = (byte) _inputOutput.readByte ();
            	int             requestLength = _inputOutput.readShort ();

            	if (requestLength == 0) // Handle big requests.
            		requestLength = _inputOutput.readInt ();

            	// Deal with server grabs.
/*            	
            	while (!_xServer.processingAllowed (this)) {
            		try {
            			sleep (100);
            		} catch (InterruptedException e) {
            		}
            	}
*/
            	synchronized (_xServer) {
            		processRequest (opcode, arg, requestLength * 4 - 4);
            	}
            }
    }
    /**
     * Process a single request from the client.
     * 
     * TODO: build those to abstract and/or interface
     *
     * @param opcode        The request's opcode.
     * @param arg   Optional first argument.
     * @param bytesRemaining        Bytes yet to be read in the request.
     * @throws IOException
     */
    private void processRequest (byte opcode,byte arg,int bytesRemaining) throws IOException {
            _sequenceNumber++;
            switch (opcode) {
                    case RequestCode.CreateWindow:
                    	byte[] data = new byte[bytesRemaining];
//                    	reqCreateWindow();
                    	break;
                    case RequestCode.ChangeWindowAttributes:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetWindowAttributes:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.DestroyWindow:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.DestroySubwindows:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ChangeSaveSet:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ReparentWindow:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.MapWindow:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.MapSubwindows:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UnmapWindow:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UnmapSubwindows:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ConfigureWindow:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CirculateWindow:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryTree:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ChangeProperty:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.DeleteProperty:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetProperty:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ListProperties:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryPointer:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetMotionEvents:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.TranslateCoordinates:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ClearArea:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ListInstalledColormaps:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.RotateProperties:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetGeometry:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CopyArea:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CopyPlane:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyPoint:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyLine:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolySegment:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyRectangle:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyArc:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.FillPoly:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyFillRectangle:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyFillArc:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PutImage:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetImage:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyText8:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.PolyText16:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ImageText8:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ImageText16:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryBestSize:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.InternAtom:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetAtomName:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetSelectionOwner:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetSelectionOwner:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ConvertSelection:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SendEvent:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GrabPointer:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UngrabPointer:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GrabButton:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UngrabButton:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ChangeActivePointerGrab:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GrabKeyboard:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UngrabKeyboard:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GrabKey:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UngrabKey:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.AllowEvents:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetInputFocus:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetInputFocus:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GrabServer:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UngrabServer:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.WarpPointer:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ChangePointerControl:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetPointerControl:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetPointerMapping:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetPointerMapping:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.OpenFont:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CloseFont:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryFont:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryTextExtents:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ListFonts:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ListFontsWithInfo:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetFontPath:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetFontPath:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CreatePixmap:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.FreePixmap:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CreateGC:reqCreateGC(this,bytesRemaining);break;
                    case RequestCode.ChangeGC:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CopyGC:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetDashes:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetClipRectangles:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.FreeGC:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CreateColormap:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CopyColormapAndFree:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.FreeColormap:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.InstallColormap:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.UninstallColormap:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.AllocColor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.AllocNamedColor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.AllocColorCells:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.AllocColorPlanes:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.FreeColors:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.StoreColors:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.StoreNamedColor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryColors:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.LookupColor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CreateCursor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.CreateGlyphCursor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.FreeCursor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.RecolorCursor:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryExtension:reqQueryExtension(this,bytesRemaining);break;
                    case RequestCode.ListExtensions:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.QueryKeymap:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ChangeKeyboardMapping:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetKeyboardMapping:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ChangeKeyboardControl:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetModifierMapping:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetModifierMapping:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetKeyboardControl:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.Bell:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetScreenSaver:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.GetScreenSaver:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ChangeHosts:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ListHosts:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetAccessControl:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.SetCloseDownMode:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.KillClient:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.ForceScreenSaver:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    case RequestCode.NoOperation:System.err.println("op_code:"+opcode+" size:"+bytesRemaining);break;
                    default:        // Opcode not implemented.
                    		System.err.println("op_code:"+opcode+" size:"+bytesRemaining);
                            break;
            }
    }
    // parsing methods
    private void reqCreateWindow() {
    	
    }

    /*
     * Protocol in bottom of 
     * http://www.x.org/releases/X11R7.5/doc/x11proto/proto.pdf
     */
    
    /**
     * The XCreateGC() function creates a graphics context and returns a GC
     * op_code: 55 
     * TODO: check GContext.java for parser and build similar with logging and pass to interface
     * @param client
     * @param bytesRemaining
     * @throws IOException
     */
    private void reqCreateGC(Client client,int bytesRemaining) throws IOException {
    	
        if (bytesRemaining < 12) {
        	_inputOutput.readSkip (bytesRemaining);
            ErrorCode.write (this, ErrorCode.Length, RequestCode.CreateGC, 0);
        } else {
            int	cid = _inputOutput.readInt ();  		 // GContext ID.
            int	drawable = _inputOutput.readInt ();		 // Drawable ID.
            
            bytesRemaining -= 8;
            System.err.println("req: CreateGC(GContext="+cid+",Drawable="+drawable+")");
            
/*            
            Resource        r = _xServer.getResource (d);
            
            if (! validResourceId (id)) {
                    _inputOutput.readSkip (bytesRemaining);
                    ErrorCode.write (this, ErrorCode.IDChoice, opcode, id);
            } else if (r == null || !r.isDrawable ()) {
                    _inputOutput.readSkip (bytesRemaining);
                    ErrorCode.write (this, ErrorCode.Drawable, opcode, d);
            } else {
                    GContext.processCreateGCRequest (_xServer, this, id,bytesRemaining);
            }*/
        }    	
    }
    
    /**
     * The XQueryExtension() function determines if the named extension is present
     * op_code: 98
     * 
     * TODO: cleanup!!!
     * TODO: names like in X documentation
     *  
     * @param client
     * @param bytesRemaining
     * @throws IOException
     */
    private void reqQueryExtension(Client client,int bytesRemaining) throws IOException {
    	if (bytesRemaining < 4) {
    		System.err.println("error in query ext data");
    		_inputOutput.readSkip (bytesRemaining);
    		ErrorCode.write (client, ErrorCode.Length,RequestCode.QueryExtension, 0);
            return;
    	}
    	int name_length = _inputOutput.readShort ();       // Length of name.
    	int pad = -name_length & 3;
    	_inputOutput.readSkip (2);        // Unused.
    	bytesRemaining -= 4;
    	
    	if (bytesRemaining != name_length + pad) {
    		_inputOutput.readSkip (bytesRemaining);
    		ErrorCode.write (client, ErrorCode.Length,RequestCode.QueryExtension, 0);
    		return;
    	}
    	// read string
    	byte[]          bytes = new byte[name_length];
    	_inputOutput.readBytes(bytes, 0, name_length);
    	_inputOutput.readSkip (pad);      // Unused.

    	String          s = new String (bytes);
    	System.err.println("req: QueryExtension("+s+")");
    	Extension e = _outClient.reqQueryExtension(s); // ask from client
    	synchronized (_inputOutput) {
            Util.writeReplyHeader (client, (byte) 0);
            _inputOutput.writeInt (0);        // Reply length.
            if (e == null) {
            	_inputOutput.writeByte ((byte) 0);        // Present. 0 = false.
            	_inputOutput.writeByte ((byte) 0);        // Major opcode.
            	_inputOutput.writeByte ((byte) 0);        // First event.
            	_inputOutput.writeByte ((byte) 0);        // First error.
            } else {
            	_inputOutput.writeByte ((byte) 1);        // Present. 1 = true.
            	_inputOutput.writeByte (e.getMajorOpcode());   // Major opcode.
            	_inputOutput.writeByte (e.getFirstEvent());    // First event.
            	_inputOutput.writeByte (e.getFirstError());    // First error.
            }
            _inputOutput.writePadBytes (20);  // Unused.
    	}
    	_inputOutput.flush ();  
    	System.err.println("rep: QueryExtension("+s+") ret:"+e);
    }

    /**
     * used in Util.java
     * @return
     */
    public InputOutput getInputOutput () {
    	return _inputOutput;
    }
    
    /**
     * used in Util.java
     * @return
     */
    public int getSequenceNumber () {
    	return _sequenceNumber;
    }
}
