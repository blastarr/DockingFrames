/*
 * Bibliothek - DockingFrames
 * Library built on Java/Swing, allows the user to "drag and drop"
 * panels containing any Swing-Component the developer likes to add.
 * 
 * Copyright (C) 2007 Benjamin Sigg
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 * Benjamin Sigg
 * benjamin_sigg@gmx.ch
 * CH - Switzerland
 */
package bibliothek.gui.dock.common;

import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;

import javax.swing.JFrame;
import javax.swing.KeyStroke;

import bibliothek.extension.gui.dock.theme.SmoothTheme;
import bibliothek.extension.gui.dock.theme.eclipse.EclipseTabDockAction;
import bibliothek.gui.DockController;
import bibliothek.gui.DockFrontend;
import bibliothek.gui.DockStation;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.DockElement;
import bibliothek.gui.dock.DockFactory;
import bibliothek.gui.dock.ScreenDockStation;
import bibliothek.gui.dock.action.ActionGuard;
import bibliothek.gui.dock.action.DockAction;
import bibliothek.gui.dock.action.DockActionSource;
import bibliothek.gui.dock.common.intern.*;
import bibliothek.gui.dock.event.DockAdapter;
import bibliothek.gui.dock.facile.action.CloseAction;
import bibliothek.gui.dock.facile.action.StateManager;
import bibliothek.gui.dock.layout.DockSituationIgnore;
import bibliothek.gui.dock.support.util.ApplicationResource;
import bibliothek.gui.dock.support.util.ApplicationResourceManager;
import bibliothek.gui.dock.themes.NoStackTheme;
import bibliothek.gui.dock.util.PropertyKey;
import bibliothek.gui.dock.util.PropertyValue;

/**
 * Manages the interaction between {@link FSingleDockable}, {@link FMultipleDockable}
 * and the {@link FContentArea}.<br>
 * Clients should call <code>read</code> and <code>write</code> of the
 * {@link ApplicationResourceManager}, accessible through {@link #getResources()}, 
 * to store or load the configuration.<br>
 * Clients which do no longer need a {@link FControl} can call {@link #destroy()}
 * to free resources.
 * @author Benjamin Sigg
 *
 */
public class FControl {
    /**
     * {@link KeyStroke} used to change a {@link FDockable} into maximized-state,
     * or to go out of maximized-state when needed.
     */
    public static final PropertyKey<KeyStroke> KEY_MAXIMIZE_CHANGE = 
        new PropertyKey<KeyStroke>( "fcontrol.maximize_change" );
    
    /**
     * {@link KeyStroke} used to change a {@link FDockable} into
     * maximized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_MAXIMIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_maximized" );
    
    /**
     * {@link KeyStroke} used to change a {@link FDockable} into
     * normalized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_NORMALIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_normalized" );
    
    /**
     * {@link KeyStroke} used to change a {@link FDockable} into
     * minimized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_MINIMIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_minimized" );
    
    /**
     * {@link KeyStroke} used to change a {@link FDockable} into
     * externalized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_EXTERNALIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_externalized" );
    
    /**
     * {@link KeyStroke} used to close a {@link FDockable}.
     */
    public static final PropertyKey<KeyStroke> KEY_CLOSE = 
        new PropertyKey<KeyStroke>( "fcontrol.close" );
    
    /** the unique id of the station that handles the externalized dockables */
    public static final String EXTERNALIZED_STATION_ID = "external";
    
    /** the unique id of the default-{@link FContentArea} created by this control */
    public static final String CONTENT_AREA_STATIONS_ID = "fcontrol";
    
    /** connection to the real DockingFrames */
	private DockFrontend frontend;
	
	/** the set of known factories */
	private Map<String, FactoryProperties> factories = 
		new HashMap<String, FactoryProperties>();
	
	/** list of all dockables registered to this control */
	private List<FDockable> dockables =
	    new ArrayList<FDockable>();
	
	/** list of all {@link FSingleDockable}s */
	private List<FSingleDockable> singleDockables =
	    new ArrayList<FSingleDockable>();
	
	/** the set of {@link FMultipleDockable}s */
	private List<FMultipleDockable> multiDockables = 
		new ArrayList<FMultipleDockable>();
	
	/** access to internal methods of some {@link FDockable}s */
	private Map<FDockable, FDockableAccess> accesses = new HashMap<FDockable, FDockableAccess>();
	
	/** a manager allowing the user to change the extended-state of some {@link FDockable}s */
	private FStateManager stateManager;
	
	/** the default location of newly opened {@link FDockable}s */
	private FLocation defaultLocation;
	
	/** the center component of the main-frame */
	private FContentArea content;
	
	/** the whole list of contentareas known to this control, includes {@link #content} */
	private List<FContentArea> contents = new ArrayList<FContentArea>();
	
	/** Access to the internal methods of this control */
	private FControlAccess access = new Access();
	
	/** manager used to store and read configurations */
	private ApplicationResourceManager resources = new ApplicationResourceManager();
	
	/** a list of listeners which are to be informed when this control is no longer in use */
	private List<DestroyHook> hooks = new ArrayList<DestroyHook>();
	
	/** factory used to create new elements for this control */
	private FControlFactory factory;

    /**
     * Creates a new control
     * @param frame the main frame of the application, needed to create
     * dialogs for externalized {@link FDockable}s
     */
    public FControl( JFrame frame ){
        this( frame, false );
    }
	
	/**
     * Creates a new control
     * @param frame the main frame of the application, needed to create
     * dialogs for externalized {@link FDockable}s
     * @param restrictedEnvironment whether this application runs in a
     * restricted environment and is not allowed to listen for global events.
     */
    public FControl( JFrame frame, boolean restrictedEnvironment ){
        this( frame, restrictedEnvironment ? new SecureControlFactory() : new EfficientControlFactory() );
    }
	
	/**
	 * Creates a new control
	 * @param frame the main frame of the application, needed to create
	 * dialogs for externalized {@link FDockable}s
	 * @param factory a factory which is used to create new elements for this
	 * control.
	 */
	public FControl( JFrame frame, FControlFactory factory ){
	    this.factory = factory;
	    
		frontend = new DockFrontend( factory.createController(), frame ){
		    @Override
		    protected void save( DataOutputStream out, boolean entry ) throws IOException {
		        super.save( out, entry );
		        stateManager.write( new StateManager.LocationStreamTransformer(), out );
		    }
		    @Override
		    protected void load( DataInputStream in, boolean entry ) throws IOException {
		        super.load( in, entry );
		        stateManager.read( new StateManager.LocationStreamTransformer(), in );
		    }
		};
		frontend.setIgnoreForEntry( new DockSituationIgnore(){
		    public boolean ignoreChildren( DockStation station ) {
		        Dockable dockable = station.asDockable();
		        if( dockable == null )
		            return false;
		        if( dockable instanceof FacileDockable ){
		            FDockable fdockable = ((FacileDockable)dockable).getDockable();
		            if( fdockable instanceof FWorkingArea )
		                return true;
		        }
		        return false;
		    }
		    public boolean ignoreElement( DockElement element ) {
		        return false;
		    }
		});
		frontend.setShowHideAction( false );
		frontend.getController().setTheme( new NoStackTheme( new SmoothTheme() ) );
		frontend.getController().addActionGuard( new ActionGuard(){
		    public boolean react( Dockable dockable ) {
		        return dockable instanceof FacileDockable;
		    }
		    public DockActionSource getSource( Dockable dockable ) {
		        return ((FacileDockable)dockable).getDockable().getClose();
		    }
		});
		frontend.getController().getRegister().addDockRegisterListener( new DockAdapter(){
		    @Override
		    public void dockableRegistered( DockController controller, Dockable dockable ) {
		        if( dockable instanceof FacileDockable ){
		            FDockableAccess access = accesses.get( ((FacileDockable)dockable).getDockable() );
		            if( access != null ){
		                access.informVisibility( true );
		            }
		        }
		    }
		    
		    @Override
		    public void dockableUnregistered( DockController controller, Dockable dockable ) {
		        if( dockable instanceof FacileDockable ){
                    FDockableAccess access = accesses.get( ((FacileDockable)dockable).getDockable() );
                    if( access != null ){
                        access.informVisibility( false );
                    }
                }
		    }
		});
		
		frontend.getController().addAcceptance( new StackableAcceptance() );
		frontend.getController().addAcceptance( new WorkingAreaAcceptance( access ) );
		frontend.getController().addAcceptance( new ExtendedModeAcceptance( access ) );
		
		try{
    		resources.put( "fcontrol.frontend", new ApplicationResource(){
    		    public void write( DataOutputStream out ) throws IOException {
                    writeWorkingAreas( out );
    		        frontend.write( out );
    		    }
    		    public void read( DataInputStream in ) throws IOException {
    		        readWorkingAreas( in );
    		        frontend.read( in );
    		    }
    		});
		}
		catch( IOException ex ){
		    System.err.println( "Non lethal IO-error:" );
		    ex.printStackTrace();
		}
		
		stateManager = new FStateManager( access );
		content = createContentArea( CONTENT_AREA_STATIONS_ID );
		
		final ScreenDockStation screen = factory.createScreenDockStation( frame );
		stateManager.add( EXTERNALIZED_STATION_ID, screen );
		frontend.addRoot( screen, EXTERNALIZED_STATION_ID );
		screen.setShowing( frame.isVisible() );
		frame.addComponentListener( new ComponentListener(){
		    public void componentShown( ComponentEvent e ) {
		        screen.setShowing( true );
		    }
		    public void componentHidden( ComponentEvent e ) {
		        screen.setShowing( false );
		    }
		    public void componentMoved( ComponentEvent e ) {
		        // ignore
		    }
		    public void componentResized( ComponentEvent e ) {
		        // ignore
		    }
		});
		
		// set some default values
		putProperty( KEY_MAXIMIZE_CHANGE, KeyStroke.getKeyStroke( KeyEvent.VK_M, InputEvent.CTRL_MASK ) );
		putProperty( KEY_GOTO_EXTERNALIZED, KeyStroke.getKeyStroke( KeyEvent.VK_E, InputEvent.CTRL_MASK ) );
		putProperty( KEY_GOTO_NORMALIZED, KeyStroke.getKeyStroke( KeyEvent.VK_N, InputEvent.CTRL_MASK ) );
		putProperty( KEY_CLOSE, KeyStroke.getKeyStroke( KeyEvent.VK_C, InputEvent.CTRL_MASK ) );
	}
	
	/**
	 * Writes a map using the unique identifiers of each {@link FSingleDockable} to
	 * tell to which {@link FWorkingArea} it belongs.
	 * @param out the stream to write into
	 * @throws IOException if an I/O error occurs
	 */
	private void writeWorkingAreas( DataOutputStream out ) throws IOException{
	    Map<String,String> map = new HashMap<String, String>();
	    
	    for( FSingleDockable dockable : singleDockables ){
	        FWorkingArea area = dockable.getWorkingArea();
	        if( area != null ){
	            map.put( dockable.getUniqueId(), area.getUniqueId() );
	        }
	    }
	    
	    out.writeInt( map.size() );
	    for( Map.Entry<String, String> entry : map.entrySet() ){
	        out.writeUTF( entry.getKey() );
	        out.writeUTF( entry.getValue() );
	    }
	}
	
	/**
	 * Reads a map telling for each {@link FSingleDockable} to which {@link FWorkingArea}
	 * it belongs.
	 * @param in the stream to read from
	 * @throws IOException if an I/O error occurs
	 */
	private void readWorkingAreas( DataInputStream in ) throws IOException{
	    Map<String, FSingleDockable> dockables = new HashMap<String, FSingleDockable>();
	    Map<String, FWorkingArea> areas = new HashMap<String, FWorkingArea>();
	    
	    for( FSingleDockable dockable : this.singleDockables ){
	        if( dockable instanceof FWorkingArea ){
	            FWorkingArea area = (FWorkingArea)dockable;
	            dockables.put( area.getUniqueId(), area );
	            areas.put( area.getUniqueId(), area );
	        }
	        else{
	            dockables.put( dockable.getUniqueId(), dockable );
	        }
	    }
	    
	    for( int i = 0, n = in.readInt(); i<n; i++ ){
	        String key = in.readUTF();
	        String value = in.readUTF();
	        
	        FDockable dockable = dockables.get( key );
	        if( dockable != null ){
	            FWorkingArea area = areas.get( value );
	            dockable.setWorkingArea( area );
	        }
	    }
	}
	
	/**
	 * Frees as much resources as possible. This {@link FControl} will no longer
	 * work correctly after this method was called.
	 */
	public void destroy(){
	    frontend.getController().kill();
	    for( DestroyHook hook : hooks )
	        hook.destroy();
	}
	
	/**
	 * Creates and adds a new {@link FWorkingArea} to this control. The area
	 * is not made visible by this method.
	 * @param uniqueId the unique id of the area
	 * @return the new area
	 */
	public FWorkingArea createWorkingArea( String uniqueId ){
	    FWorkingArea area = factory.createWorkingArea( uniqueId );
	    add( area );
	    return area;
	}
	
	/**
	 * Creates and adds a new {@link FContentArea}.
	 * @param uniqueId the unique id of the new contentarea, the id must be unique
	 * in respect to all other contentareas which are registered at this control.
	 * @return the new contentarea
	 * @throws IllegalArgumentException if the id is not unique
	 * @throws NullPointerException if the id is <code>null</code>
	 */
	public FContentArea createContentArea( String uniqueId ){
		if( uniqueId == null )
			throw new NullPointerException( "uniqueId must not be null" );
		
		for( FContentArea center : contents ){
			if( center.getUniqueId().equals( uniqueId ))
				throw new IllegalArgumentException( "There exists already a FContentArea with the unique id " + uniqueId );
		}
		
		FContentArea center = new FContentArea( access, uniqueId );
		contents.add( center );
		return center;
	}
	
	/**
	 * Removes <code>content</code> from the list of known contentareas. This also removes
	 * the stations of <code>content</code> from this control. Elements aboard the
	 * stations are made invisible, but not removed from this control.
	 * @param content the contentarea to remove
	 * @throws IllegalArgumentException if the default-contentarea equals <code>content</code>
	 */
	public void removeContentArea( FContentArea content ){
		if( this.content == content )
			throw new IllegalArgumentException( "The default-contentarea can't be removed" );
		
		if( contents.remove( content ) ){
			frontend.removeRoot( content.getCenter() );
			frontend.removeRoot( content.getEast() );
			frontend.removeRoot( content.getWest() );
			frontend.removeRoot( content.getNorth() );
			frontend.removeRoot( content.getSouth() );
			
			stateManager.remove( content.getCenterIdentifier() );
			stateManager.remove( content.getEastIdentifier() );
			stateManager.remove( content.getWestIdentifier() );
			stateManager.remove( content.getNorthIdentifier() );
			stateManager.remove( content.getSouthIdentifier() );
		}
	}
	
	/**
	 * Gets an unmodifiable list of all {@link FContentArea}s registered at
	 * this control
	 * @return the list of contentareas
	 */
	public List<FContentArea> getContentAreas(){
		return Collections.unmodifiableList( contents );
	}
	
	/**
	 * Gets the factory which is mainly used to create new elements for this
	 * control.
	 * @return the factory
	 */
	public FControlFactory getFactory() {
        return factory;
    }

	/**
	 * Adds a destroy-hook. The hook is called when this {@link FControl} is
	 * destroyed through {@link #destroy()}.
	 * @param hook the new hook
	 */
	public void addDestroyHook( DestroyHook hook ){
	    if( hook == null )
	        throw new NullPointerException( "hook must not be null" );
	    hooks.add( hook );
	}
	
	/**
	 * Removes a destroy-hook from this {@link FControl}.
	 * @param hook the hook to remove
	 */
	public void removeDestroyHook( DestroyHook hook ){
	    hooks.remove( hook );
	}
	
	/**
	 * Grants access to the manager that reads and stores configurations
	 * of the facile-framework.<br>
	 * Clients can add their own {@link ApplicationResource}s to this manager,
	 * however clients are strongly discouraged from removing {@link ApplicationResource}
	 * which they did not add by themself.
	 * @return the persistent storage
	 */
	public ApplicationResourceManager getResources() {
        return resources;
    }
	
	/**
	 * Changes the value of a property. Some properties are:
	 * <ul>
	 * <li>{@link #KEY_MAXIMIZE_CHANGE}</li>
	 * <li>{@link #KEY_GOTO_EXTERNALIZED}</li>
	 * <li>{@link #KEY_GOTO_MAXIMIZED}</li>
	 * <li>{@link #KEY_GOTO_MINIMIZED}</li>
	 * <li>{@link #KEY_GOTO_NORMALIZED}</li>
	 * <li>{@link #KEY_CLOSE}</li>
	 * </ul>
	 * @param <A> the type of the value
	 * @param key the name of the property
	 * @param value the new value, can be <code>null</code>
	 */
	public <A> void putProperty( PropertyKey<A> key, A value ){
	    frontend.getController().getProperties().set( key, value );
	}
	
	/**
	 * Gets the value of a property.
	 * @param <A> the type of the property
	 * @param key the name of the property
	 * @return the value or <code>null</code>
	 */
	public <A> A getProperty( PropertyKey<A> key ){
	    return frontend.getController().getProperties().get( key );
	}
	
	/**
	 * Gets the element that should be in the center of the mainframe.
	 * @return the center of the mainframe of the application
	 */
	public FContentArea getContentArea() {
        return content;
    }
	
	/**
	 * Adds a dockable to this control. The dockable can be made visible afterwards.
	 * @param <F> the type of the new element
	 * @param dockable the new element to show
	 * @return <code>dockable</code>
	 */
	public <F extends FSingleDockable> F add( F dockable ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() != null )
			throw new IllegalStateException( "dockable is already part of a control" );

		dockable.setControl( access );
		String id = "single " + dockable.getUniqueId();
		accesses.get( dockable ).setUniqueId( id );
		frontend.add( dockable.intern(), id );
		frontend.setHideable( dockable.intern(), true );
		dockables.add( dockable );
		singleDockables.add( dockable );
		return dockable;
	}
	
	/**
	 * Adds a dockable to this control. The dockable can be made visible afterwards.
	 * @param <F> the type of the new element
	 * @param dockable the new element to show
	 * @return <code>dockable</code>
	 */
	public <F extends FMultipleDockable> F add( F dockable ){
	    String factory = access.getFactoryId( dockable.getFactory() );
        if( factory == null ){
            throw new IllegalStateException( "the factory for a MultipleDockable is not registered" );
        }
        
        int count = 0;
        for( FMultipleDockable multi : multiDockables ){
            if( factory.equals( access.getFactoryId( multi.getFactory() )))
                count++;
        }
        String id = "multi " + count + " " + factory;
        return add( dockable, id );
	}

	/**
     * Adds a dockable to this control. The dockable can be made visible afterwards.
     * @param <F> the type of the new element
     * @param dockable the new element to show
     * @param uniqueId id the unique id of the new element
     * @return <code>dockable</code>
     */
	private <F extends FMultipleDockable> F add( F dockable, String uniqueId ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() != null )
			throw new IllegalStateException( "dockable is already part of a control" );
		
		dockable.setControl( access );
		accesses.get( dockable ).setUniqueId( uniqueId );
		multiDockables.add( dockable );
		dockables.add( dockable );
		return dockable;
	}
	
	/**
	 * Removes a dockable from this control. The dockable is made invisible.
	 * @param dockable the element to remove
	 */
	public void remove( FSingleDockable dockable ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() == access ){
			dockable.setVisible( false );
			frontend.remove( dockable.intern() );
			dockables.remove( dockable );
			singleDockables.remove( dockable );
			dockable.setControl( null );
		}
	}
	
	/**
	 * Removes a dockable from this control. The dockable is made invisible.
	 * @param dockable the element to remove
	 */
	public void remove( FMultipleDockable dockable ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() == access ){
			dockable.setVisible( false );
			frontend.remove( dockable.intern() );
			multiDockables.remove( dockable );
			dockables.remove( dockable );
			String factory = access.getFactoryId( dockable.getFactory() );
			
			if( factory == null ){
				throw new IllegalStateException( "the factory for a MultipleDockable is not registered" );
			}
			
			factories.get( factory ).count--;
			dockable.setControl( null );
		}
	}
	
	/**
	 * Adds a factory to this control. The factory will create {@link FMultipleDockable}s
	 * when a layout is loaded.
	 * @param id the unique id of the factory
	 * @param factory the new factory
	 */
	public void add( final String id, final FMultipleDockableFactory factory ){
		if( id == null )
			throw new NullPointerException( "id must not be null" );
		
		if( factory == null )
			throw new NullPointerException( "factory must not be null" );
		
		if( factories.containsKey( id )){
			throw new IllegalArgumentException( "there is already a factory named " + id );
		}
		
		FactoryProperties properties = new FactoryProperties();
		properties.factory = factory;
		
		factories.put( id, properties );
		
		frontend.registerFactory( new DockFactory<FacileDockable>(){
			public String getID(){
				return id;
			}

			public FacileDockable read( Map<Integer, Dockable> children, boolean ignoreChildren, DataInputStream in ) throws IOException{
			    // id
				String id = in.readUTF();
				
				// content
			    FMultipleDockable dockable = factory.read( in );
			    if( dockable == null )
			        return null;
			    
			    add( dockable, id );
			    
			    // working area
			    if( in.readBoolean() ){
			        String areaId = in.readUTF();
			        for( FDockable check : dockables ){
			            if( check instanceof FWorkingArea ){
			                FWorkingArea checkArea = (FWorkingArea)check;
			                if( checkArea.getUniqueId().equals( areaId )){
			                    // found
			                    dockable.setWorkingArea( checkArea );
			                    break;
			                }
			            }
			        }
			    }
			    
			    return dockable.intern();
			}

			public void read( Map<Integer, Dockable> children, boolean ignoreChildren, FacileDockable preloaded, DataInputStream in ) throws IOException{
				// ignore
			}

			public void write( FacileDockable element, Map<Dockable, Integer> children, DataOutputStream out ) throws IOException{
			    FMultipleDockable fdockable = (FMultipleDockable)element.getDockable();
			    
			    // unique id
			    out.writeUTF( accesses.get( element.getDockable() ).getUniqueId() );

                // content
                factory.write( fdockable, out );
			    
			    // working area if present
				if( fdockable.getWorkingArea() == null ){
				    out.writeBoolean( false );
				}
				else{
				    out.writeBoolean( true );
				    out.writeUTF( fdockable.getWorkingArea().getUniqueId() );
				}
			}
		});
	}
	
	/**
	 * Sets the location where {@link FDockable}s are opened when there is
	 * nothing else specified for these <code>FDockable</code>s.
	 * @param defaultLocation the location, can be <code>null</code>
	 */
	public void setDefaultLocation( FLocation defaultLocation ){
		this.defaultLocation = defaultLocation;
	}
	
	/**
	 * Gets the location where {@link FDockable}s are opened when nothing else
	 * is specified.
	 * @return the location, might be <code>null</code>
	 * @see #setDefaultLocation(FLocation)
	 */
	public FLocation getDefaultLocation(){
		return defaultLocation;
	}
	
	/**
	 * Sets the {@link FMaximizeBehavior}. The behavior decides what happens
	 * when the user want's to maximize or to un-maximize a {@link FDockable}.
	 * @param behavior the new behavior, not <code>null</code>
	 */
	public void setMaximizeBehavior( FMaximizeBehavior behavior ){
		stateManager.setMaximizeBehavior( behavior );
	}
	
	/**
	 * Gets the currently used maximize-behavior.
	 * @return the behavior, not <code>null</code>
	 * @see #setMaximizeBehavior(FMaximizeBehavior)
	 */
	public FMaximizeBehavior getMaximizeBehavior(){
		return stateManager.getMaximizeBehavior();
	}
	
	/**
	 * Gets the representation of the layer beneath the facile-layer.
	 * @return the entry point to DockingFrames
	 */
	public DockFrontend intern(){
		return frontend;
	}
	
	/**
	 * Writes the current and all known layouts into <code>file</code>.
	 * @param file the file to override
	 * @throws IOException if the file can't be written
	 */
	public void write( File file ) throws IOException{
		DataOutputStream out = new DataOutputStream( new BufferedOutputStream( new FileOutputStream( file )));
		write( out );
		out.close();
	}
	
	/**
	 * Writes the current and all known layouts into <code>out</code>.
	 * @param out the stream to write into
	 * @throws IOException if the stream is not writable
	 */
	public void write( DataOutputStream out ) throws IOException{
		out.writeInt( 1 );
		frontend.write( out );
	}
	
	/**
	 * Reads the current and other known layouts from <code>file</code>.
	 * @param file the file to read from
	 * @throws IOException if the file can't be read
	 */
	public void read( File file ) throws IOException{
		DataInputStream in = new DataInputStream( new BufferedInputStream( new FileInputStream( file )));
		read( in );
		in.close();
	}
	
	/**
	 * Reads the current and other known layouts from <code>in</code>.
	 * @param in the stream to read from
	 * @throws IOException if the stream can't be read
	 */
	public void read( DataInputStream in ) throws IOException{
		int version = in.readInt();
		if( version != 1 )
			throw new IOException( "Version of stream unknown, expected 1 but found: " + version );
		
		frontend.read( in );
	}
	
	/**
	 * Stores the current layout with the given name.
	 * @param name the name of the current layout.
	 */
	public void save( String name ){
		frontend.save( name );
	}
	
	/**
	 * Loads an earlier stored layout.
	 * @param name the name of the layout.
	 */
	public void load( String name ){
		frontend.load( name );
	}
	
	/**
	 * Deletes a layout that has been stored earlier.
	 * @param name the name of the layout to delete
	 */
	public void delete( String name ){
		frontend.delete( name );
	}
	
	/**
	 * Gets a list of all layouts that are currently known.
	 * @return the list of layouts
	 */
	public String[] layouts(){
		Set<String> settings = frontend.getSettings();
		return settings.toArray( new String[ settings.size() ] );
	}
	
	
	/**
	 * Properties associated with one factory.
	 * @author Benjamin Sigg
	 *
	 */
	private class FactoryProperties{
		/** the associated factory */
		public FMultipleDockableFactory factory;
		/** the number of {@link FMultipleDockable} that belong to {@link #factory} */
		public int count = 0;
	}
	
	/**
	 * Ensures that all {@link Dockable}s are in a valid state (minimized,
	 * maximized, normalized or externalized).
	 */
	protected void ensureValidModes(){
	    for( FDockable dockable : dockables.toArray( new FDockable[ dockables.size() ] ) ){
	        stateManager.ensureValidLocation( dockable );
	    }
	}
	
	/**
	 * A class giving access to the internal methods of the enclosing
	 * {@link FControl}.
	 * @author Benjamin Sigg
	 */
	private class Access implements FControlAccess{
	    /** action used to close {@link FDockable}s  */
	    private FCloseAction closeAction;
	    
	    public FControl getOwner(){
			return FControl.this;
		}
	    
	    public void link( FDockable dockable, FDockableAccess access ) {
	        if( access == null )
	            accesses.remove( dockable );
	        else{
	            accesses.put( dockable, access );
	        }
	    }
	    
	    public FDockableAccess access( FDockable dockable ) {
	        return accesses.get( dockable );
	    }
		
		public void hide( FDockable dockable ){
			frontend.hide( dockable.intern() );
			ensureValidModes();
		}
		
		public void show( FDockable dockable ){
			FDockableAccess access = access( dockable );
			FLocation location = null;
			if( access != null ){
				location = access.internalLocation();
			}
			if( location == null ){
				if( !frontend.hasLocation( dockable.intern() ))
					location = defaultLocation;
			}
			
			frontend.show( dockable.intern() );
			
			if( location != null ){
				stateManager.setLocation( dockable.intern(), location );
			}
			stateManager.ensureValidLocation( dockable );
		}
		
		public boolean isVisible( FDockable dockable ){
			return frontend.isShown( dockable.intern() );
		}
		
		public String getFactoryId( FMultipleDockableFactory factory ){
			for( Map.Entry<String, FactoryProperties> entry : factories.entrySet() ){
				if( entry.getValue().factory == factory )
					return entry.getKey();
			}
			
			return null;
		}
		
		public FStateManager getStateManager() {
		    return stateManager;
		}
		
		public DockAction createCloseAction( final FDockable fdockable ) {
		    if( closeAction == null )
		        closeAction = new FCloseAction();
		    
		    return closeAction;
		}
	}
	
	/**
	 * Action that can close {@link FDockable}s
	 * @author Benjamin Sigg
	 */
	@EclipseTabDockAction
	private class FCloseAction extends CloseAction{
	    /**
	     * Creates a new action
	     */
	    public FCloseAction(){
	        super( frontend.getController() );
	        new PropertyValue<KeyStroke>( KEY_CLOSE, frontend.getController() ){
	            @Override
	            protected void valueChanged( KeyStroke oldValue, KeyStroke newValue ) {
	                setAccelerator( newValue );
	            }
	        };
	    }
	    
	    @Override
        protected void close( Dockable dockable ) {
	        FDockable fdockable = ((FacileDockable)dockable).getDockable();
	        if( fdockable.getExtendedMode() == FDockable.ExtendedMode.MAXIMIZED )
	            fdockable.setExtendedMode( FDockable.ExtendedMode.NORMALIZED );
	        fdockable.setVisible( false );
        }
	}
}