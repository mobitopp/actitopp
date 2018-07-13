package edu.kit.ifv.mobitopp.actitopp;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Tim Hilgert
 * 
 * Coordinator-Klasse, die die Erstellung der Wochen-Aktivit�tenpl�ne koordiniert.
 * Wird aufgerufen von ActiToppPerson zur Erstellung der Schedules
 *
 */
public class Coordinator
{

    ////////////////
    
    //	VARIABLEN-DEKLARATIONEN
    
    //////////////// 
	
    private ActitoppPerson person;
    private HWeekPattern pattern;

    private ModelFileBase fileBase;
    private RNGHelper randomgenerator;
   
    // important for Step8C: dtd tables must be modified after each MC-selection
    // process
    // After the first MC-selection we must these modified tables instead of the
    // original ones
    // each activity type gets one of these per category (1 table per (activity
    // type, week and person) * categories) -> WELST * 15
    private DiscreteTimeDistribution[][] modifiedActDurationDTDs;

    // start time for work and education categories: WE * 16
    private DiscreteTimeDistribution[][] modifiedTourStartDTDs;
    
    
    // Important for modeling joint actions
    
   	int[] numberofactsperday_lowerboundduetojointactions = {0,0,0,0,0,0,0};
  	int[] numberoftoursperday_lowerboundduetojointactions = {0,0,0,0,0,0,0};
    
    
    
    ////////////////
    
    //	KONSTANTEN
    
    ////////////////    
    
    // Konstante f�r die Nutzung der Zeitverteilungen der Aktivit�tsdauern
    private static final int INDICATOR_ACT_DURATIONS = 0;
    // Konstante f�r die Nutzung der Zeitverteilungen der Startzeiten
    private static final int INDICATOR_TOUR_STARTTIMES = 1;



    /**
     * 
     * Konstruktor
     * 
     * @param person
     * @param personIndex
     * @param fileBase
     */
    public Coordinator(ActitoppPerson person, ModelFileBase fileBase, RNGHelper randomgenerator)
    {
    	assert person!=null : "Person nicht initialisiert";
    	assert fileBase!=null : "FileBase nicht initialisiert";
    	assert randomgenerator!=null : "Zufallszahlengenerator nicht initialisiert";
    	
    	this.person = person;
    	this.pattern = person.getWeekPattern();
      this.fileBase = fileBase;
      this.randomgenerator = randomgenerator;
         
      modifiedActDurationDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_ACT_DURATION_CLASSES];
      modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];

    }
    
 
    
  /**
   * 
   * (Main-)Methode zur Koordination der einzelnen Modellschritte
   *
   * @return
   * @throws FileNotFoundException
   * @throws IOException
   * @throws PrerequisiteNotMetException
   * @throws InvalidPersonPatternException
   */
  public void executeModel() throws FileNotFoundException, IOException, InvalidPersonPatternException, InvalidHouseholdPatternException
  {
  	
  	// Durchf�hrung der Modellschritte
  
    // Gemeinsame Aktivit�ten
    if (Configuration.model_joint_actions) 
    {
    	processJointActivities();
    	// addJointActivitiestoPattern();
    }
  	
  	
    executeStep1("1A", "anztage_w");
    executeStep1("1B", "anztage_e");
    executeStep1("1C", "anztage_l");
    executeStep1("1D", "anztage_s");
    executeStep1("1E", "anztage_t");
    executeStep1("1F", "anztage_immobil");
       
    executeStep2("2A");
    
    executeStep3("3A");
    executeStep3("3B");
    
    executeStep4("4A");
    
    executeStep5("5A");
    executeStep5("5B");
    
    executeStep6("6A"); 

    // Gemeinsame Aktivit�ten
    if (Configuration.model_joint_actions) 
    {
    	placeJointActivitiesIntoPattern();
    }

    executeStep7DC("7A", 'W');
    executeStep7DC("7B", 'E');
    executeStep7DC("7C", 'L');
    executeStep7DC("7D", 'S');
    executeStep7DC("7E", 'T');
    
    executeStep7MC("7K", 'W');
    executeStep7MC("7L", 'E');
    executeStep7MC("7M", 'L');
    executeStep7MC("7N", 'S');
    executeStep7MC("7O", 'T');
  
    executeStep8A("8A");
    executeStep8_MainAct("8B", "8C");
    executeStep8_MainAct("8D", "8E");
    executeStep8_NonMainAct("8J", "8K");

    executeStep9A("9A");
    
    executeStep10A("10A");
       
    // Variante 1 zur Generierung der Startzeiten
    /*       
    executeStep10B("10B");
    executeStep10C("10C");
    executeStep10D("10D");
    executeStep10E("10E");
    executeStep10GH();
    executeStep10JK();
    */			
    
    // Variante 2 zur Generierung der Startzeiten - bevorzugt
    
    createTourStartTimesDueToScheduledActivities();
    
    executeStep10("10M","10N", 1);
    executeStep10("10O","10P", 2);
    executeStep10("10Q","10R", 3);
    executeStep10ST();
    
   	// Erstelle Startzeiten f�r jede Aktivit�t 
    //createStartTimesforActivities();
    
    // Gemeinsame Aktivit�ten
    if (Configuration.model_joint_actions) 
    {
    	executeStep11("11");
  		// Bestimme, welche gemeinsamen Wege/Aktivit�ten welche anderen Personen beeinflussen
  		generateJointActionsforOtherPersons();
  		
    }
    
					 
    // Finalisierung der Wochenaktivit�tenpl�ne 
    
    // 2) Erstelle eine Liste mit allen Aktivit�ten der Woche
    List<HActivity> allModeledActivities = pattern.getAllOutofHomeActivities();    	
    HActivity.sortActivityListInWeekOrder(allModeledActivities);
	
    // 3) Erzeuge Zuhause-Aktivit�ten zwischen den Touren
    createHomeActivities(allModeledActivities);
    
    // 4) Wandel die Aktivit�tenzwecke des Modells zur�ck in mobiTopp-Aktivit�tenzwecke
    convertactiToppPurposesTomobiToppPurposeTypes(pattern.getAllActivities());
    
    // DEBUG
    if (Configuration.debugenabled)
    {
    	pattern.printAllActivitiesList();
    }
    
    // first sanity checks: check for overlapping activities. if found,
    // throw exception and redo activityweek
    weekPatternisFreeofOverlaps(pattern);

  }
  
  private void processJointActivities()
  {
  	
  	/*
  	 * Idee:
  	 * 
  	 * - Pr�fe die Liste der gemeinsamen Aktivit�ten auf zeitliche �berschneidungen, d.h. Konflikte
  	 * - Entferne Konfliktaktivit�ten
  	 * - Bestimme untere Grenzen f�r die Anzahl an Touren und Aktivit�ten an jedem Tag basierend auf der Liste gemeinsamer Aktivit�ten
  	 * 
  	 * - Modelliere Anzahl an Touren und Aktivit�ten 
  	 * 		- Es muss an jedem Tag mit gemeinsamen Aktivit�ten mindestens eine Tour geben!
  	 * 		- Bei mehr als 2 gemeinsamen Aktivit�ten mindestens 2 Touren!
  	 * 		- Es gibt f�r Haupt- und Vorheraktivit�tenzahl keine Mindestanzahlen. Aktualisiere die Zahl der bereits modellierten Aktivit�ten
  	 * 		  und lege die Grenze nur bei den Nachheraktivit�ten fest. Bei mehreren Touren auf jede Tour aufteilen!
  	 * 
  	 * - Nach Schritt 6: Aktivit�ten platzieren
  	 * 
  	 * Regelbasiert:
  	 * - Wenn Tourindex der urspr�nglichen Aktivit�t vorhanden ist, dann f�ge Sie in diese Tour ein
  	 * - Wenn Aktindex der ursp�nglichen Aktivit�t in der Tour vorhanden ist, f�ge Sie an dem Platz ein	
  	 * 
  	 * - Wenn Tour oder Aktindex noch nicht vorhanden sind, nehme den n�chstgelegenen Index, der noch nicht durch gemeinsame Akt belegt ist
  	 * 		Bsp. einzuf�gende Akt ist 1/1/3, h�chster Index ist aber 1/1/2, dann ersetze Akt 1/1/2 mit der gemeinsamen Akt
  	 * 
  	 * - Zeit�berlappungen beachten! Wenn Liste chronologisch abgearbeitet wird, bestimmt die Position der zuvor eingef�gten Aktivit�t die untere Grenze
  	 * 
  	 * - Gemeinsame Akt mit Typ 1 oder 3 von zuhause aus muss immer erste Akt in Tour sein, auch bei eingef�gter Tour	
  	 * 
  	 */
  	
 //TODO Methode zum eliminieren von �berschneidungen in ActitoppPerson.java �berf�hren
  	
 // TODO Konfliktfreiheit muss direkt in ActiToppPerson gepr�ft werden, wenn die Akt in die Liste aufgenommen wird!
  	
  	/*
  	 * Eliminiere sich �berschneidende Aktivit�ten
  	 */
  	List<HActivity> listgemakt = person.getAllJointActivitiesforConsideration();
  	HActivity.sortActivityListInWeekOrder(listgemakt);
  	
  	List<HActivity> listgemaktohnekonflikte = new ArrayList<HActivity>();
  	int anzaktmitkonflikten=0;
  	
  	// Durlaufe die urspr�ngliche Liste und f�ge Sie in die konfliktfreie ein, falls keine Konflikte vorliegen
  	for (HActivity act : listgemakt)
  	{
  		boolean aktistkonfliktfrei=true;
  		for(HActivity tmpact : listgemaktohnekonflikte)
  		{
  			if (act.checkOverlappingtoOtherActivity(tmpact)) 
  			{
  				System.err.println("Conflicting: " + act);
  				aktistkonfliktfrei=false;
  			}
  		}
  		if (aktistkonfliktfrei) 
  		{
  			listgemaktohnekonflikte.add(act);
  		}
  		else
  		{
  			anzaktmitkonflikten+=1;
  		}
  	}
  	
  	if (anzaktmitkonflikten>0)
  	{
  		System.err.println("Methode " + Thread.currentThread().getStackTrace()[2].getMethodName() + " // " + anzaktmitkonflikten + " von " + listgemakt.size() + " Akt mit Konflikten");
  	}
  	
  	person.setAllJointActivitiesforConsideration(listgemaktohnekonflikte);
  	
  	/*
  	 * Bestimme Mindestzahl an Touren und Aktivit�ten basierend auf den bereits vorhandenen gemeinsamen Aktivit�ten
  	 */
  	  	
  	for (HActivity act : listgemaktohnekonflikte)
  	{
  		// Z�hle Anzahl der Aktivit�ten hoch
  		numberofactsperday_lowerboundduetojointactions[act.getIndexDay()] += 1;
  		
  		// Bestimme Mindestzahl an Touren
  		// Bei max. 2 Aktivit�ten nur eine Tour mindestens
  		if (numberofactsperday_lowerboundduetojointactions[act.getIndexDay()] <= 2) 
  		{
  			numberoftoursperday_lowerboundduetojointactions[act.getIndexDay()] = 1;
  		}
  		// Bei mehr als 2 Aktivit�ten mindestens zwei Touren
  		else
  		{
  			numberoftoursperday_lowerboundduetojointactions[act.getIndexDay()] = 2;
  		}
  	}
    	
  }
  
  
  private void placeJointActivitiesIntoPattern()
  {
  	
   	List<HActivity> listgemakt = person.getAllJointActivitiesforConsideration();
  	HActivity.sortActivityListInWeekOrder(listgemakt);

  	/*
  	 * Aktivit�tenliste in Wochensortierung durchgehen und bestehenden Aktivit�t durch gemeinsame aus der Liste ersetzen
  	 * 
  	 */
  	
  	for (int indexinliste=0 ; indexinliste < listgemakt.size(); indexinliste++)
  	{
  		HActivity gemakt = listgemakt.get(indexinliste);
  		
  		// Indextag der Aktivit�t bestimmen
  		int gemakt_tagindex = gemakt.getIndexDay();
  		// Tourindex der Aktivit�t bestimmen
  		int gemakt_tourindex = gemakt.getTour().getIndex();
  		// Aktindex der Aktivit�t bestimmen
  		int gemakt_aktindex = gemakt.getIndex();
  		// JointStatus der Aktivit�t bestimmen
  		int gemakt_jointStatus = gemakt.getJointStatus();
  		
    	/*
    	 * Bestimme m�gliche Aktivit�ten, die ersetzt werden k�nnen
    	 */
  		List <HActivity> possibleact = new ArrayList<HActivity>();
  		
  		/*
  		 *  Schritt 1: Alle verf�gbaren Aktivit�ten des Tages
  		 */
  		{
	    	for (HActivity act : pattern.getDay(gemakt_tagindex).getAllActivitiesoftheDay())
	    	{
	    		possibleact.add(act);
	    	}
	    	HActivity.sortActivityList(possibleact);
  		}
  		
    	/*
    	 *  Schritt 2: 	Bestimme, ob es bereits getauschte Aktivit�ten an diesem Tag gibt. F�ge nur Aktivit�ten, die nach der letzten
    	 *  						getauschten liegene in eine neue Liste hinzu und arbeite mit dieser weiter
    	 */
	  	{
	    	HActivity letzteaktgetauscht=null;
	    	for (HActivity act : possibleact)
	    	{
	    		if ((act.getAttributefromMap("actreplacedbyjointact")!= null ? act.getAttributefromMap("actreplacedbyjointact") : 0) == 1.0) letzteaktgetauscht=act;
	    	}
	    	if (letzteaktgetauscht!=null)
	    	{
	    		List<HActivity> possibleactlaterinweek = new ArrayList<HActivity>();
	    		for (HActivity act : possibleact)
	    		{
	    			if (act.compareTo(letzteaktgetauscht) < 0) possibleactlaterinweek.add(act);
	    		}
	    		possibleact = possibleactlaterinweek;
	    	}
	  	}
	  	
    	/*
    	 * Schritt 3:	Bestimme, ob es weitere gemeinsame Aktivit�ten an dem Tag gibt, die noch getauscht werden m�ssen
    	 * 						Entferne entsprechend die letzten X Eintr�ge aus der Liste m�glicher Aktivit�ten, damit diese noch Platz finden!
    	 */
	  	{
		  	int anzweiteregemaktamtag=0;
	    	for (int i=indexinliste+1; i<listgemakt.size(); i++)
	    	{
	    		HActivity act = listgemakt.get(i);
	    		if (act.getIndexDay()== gemakt_tagindex) anzweiteregemaktamtag += 1;
	    	}
	    	if (anzweiteregemaktamtag>0)
	    	{
	    		for (int i=1; i<=anzweiteregemaktamtag; i++)
	    		{
	    			int letzterindex = possibleact.size()-1;
	    			possibleact.remove(letzterindex);
	    		}
	    	}
	  	}
    	
    	/*
    	 * Schritt 4: Sicherstellen, dass Liste m�glicher Aktivit�ten nicht leer ist
    	 */
    	assert possibleact.size()!=0 : "Liste der Aktivit�ten zur Ersetzung ist leer!";
  		
    	
    	/*
    	 * Schritt 5: Gemeinsame Akt von Typ 1 oder 3, d.h. mit gemeinsamem Hinweg muss, falls es sich um die erste Aktivit�t
    	 *  					auf der Tour handelt auch bei der eingef�gten Aktivit�t die erste der Tour sein.
    	 *  
    	 *  					Such in solchen F�llen alle andere ersten Aktivit�t von Touren in der Liste m�glicher Aktivit�ten und 
    	 *  					arbeite mit der neuen Liste weiter
    	 */
    	{
	    	if ((gemakt_jointStatus==1 || gemakt_jointStatus==3) && gemakt.isActivityFirstinTour())
	    	{
	    		List<HActivity> possibleactersteaktintour = new ArrayList<HActivity>();
	    		for (HActivity act : possibleact)
	    		{
	    			if (act.isActivityFirstinTour()) possibleactersteaktintour.add(act);
	    		}
	    		possibleact = possibleactersteaktintour;
	    	}
    	}
    	
    	
    	/*
    	 * Schritt 6: Pr�fen, ob List aufgrund von Schritt 5 m�glicherweise leer ist.
    	 * 						Falls ja, kann Aktivit�t nicht eigef�gt werden.
    	 */
    	if (possibleact.size()==0) 
    	{
    		System.err.println("Akt konnte nicht ersetzt werden! Schritt 6");
    		break;
    	}
    	
    	
    	/*
    	 * Schritt 6: Pr�fen, ob der Tourindex der gemeinsamen Akt in den m�glichen Akt vorhanden ist (Prio zum Ersetzen!)
    	 * 						Falls ja, dann pr�fen, ob es den Aktindex auf der Tour auch gibt (Prio zum Ersetzen!)
    	 */
    	{
	    	// F�ge alle Akt mit gleichem Tourindex in eine eigene Liste ein
	    	List<HActivity> possibleactgleichertourindex = new ArrayList<HActivity>();
	    	for (HActivity act : possibleact)
	    	{
	    		if (act.getTour().getIndex() == gemakt_tourindex)
	    		{
	    			possibleactgleichertourindex.add(act);
	    		}
	    	}
	    	// Falls Aktivit�t mit gleichen Tourindex existieren, arbeite mit dieser Liste weiter
	    	if (possibleactgleichertourindex.size()!=0)
	    	{
	    		possibleact = possibleactgleichertourindex;
	    	
	    		// F�ge alle Akt mit gleichem Aktindex in eine eigene Liste ein
	    		List<HActivity> possibleactgleicheraktindex = new ArrayList<HActivity>();
	      	for (HActivity act : possibleact)
	      	{
	      		if (act.getIndex() == gemakt_aktindex)
	      		{
	      			possibleactgleicheraktindex.add(act);
	      		}
	      	}
	      	// Falls Aktivit�t mit gleichen Aktindex existiert, arbeite mit dieser Liste weiter
	      	if (possibleactgleicheraktindex.size()!=0)
	      	{
	      		possibleact = possibleactgleicheraktindex;
	      	}
	    	}
    	}
    	
    	/*
    	 * Schritt 7: Sicherstellen, dass Liste m�glicher Aktivit�ten nicht leer ist
    	 */
    	assert possibleact.size()!=0 : "Liste der Aktivit�ten zur Ersetzung ist leer!";
    	
    	/*
    	 * Schritt 8: W�hle zuf�llig eine der verbleibenden m�glichen Aktivit�ten
    	 */
    	int zufallszahl = randomgenerator.getRandomValueBetween(0, possibleact.size()-1, 1);
    	HActivity actforreplacement = possibleact.get(zufallszahl);
    	
    	/*
    	 * Schritt 9: Aktivit�t durch gemeinsame Aktivit�t ersetzen
    	 */
    	{
	    	// Aktivit�teneigenschaften ermitteln
	    	int gemakt_duration = gemakt.getDuration();
	    	int gemakt_starttime = gemakt.getStartTime();
	    	char gemakt_acttype = gemakt.getType(); 		
	    	int gemakt_creatorPersonIndex = gemakt.getCreatorPersonIndex();		
	    	
	    	int gemakt_durationtripbefore = gemakt.getEstimatedTripTimeBeforeActivity();
	    	
	    	// Aktivit�t markieren
	    	actforreplacement.addAttributetoMap("actreplacedbyjointact", 1.0);
	    	
	    	// Je nach Art der Gemeinsamkeit unterschiedliche Aktivit�teneigenschaften ersetzen
	    	switch(gemakt_jointStatus)
				{
					// Weg davor und Aktivit�t werden gemeinsam durchgef�hrt
					case 1:
					{
						// Weg erzeugen
						actforreplacement.setTripbeforeactivity(new HTrip(actforreplacement, gemakt_durationtripbefore));
						
						// Akteigenschaften ersetzen
						actforreplacement.setDuration(gemakt_duration);
						actforreplacement.setStartTime(gemakt_starttime);
						actforreplacement.setType(gemakt_acttype);
						actforreplacement.setJointStatus(gemakt_jointStatus);
						actforreplacement.addAttributetoMap("CreatorPersonIndex", (double) gemakt_creatorPersonIndex); 
						break;
					}
					// Nur Aktivit�t wird gemeinsam durchgef�hrt
					case 2:
					{
						// Akteigenschaften ersetzen
						actforreplacement.setDuration(gemakt_duration);
						actforreplacement.setStartTime(gemakt_starttime);
						actforreplacement.setType(gemakt_acttype);
						actforreplacement.setJointStatus(gemakt_jointStatus);
						actforreplacement.addAttributetoMap("CreatorPersonIndex", (double) gemakt_creatorPersonIndex); 
						break;
					}		
					// Nur Weg davor wird gemeinsam durchgef�hrt
					case 3:
					{
						// Weg erzeugen
						actforreplacement.setTripbeforeactivity(new HTrip(actforreplacement, gemakt_durationtripbefore));
						actforreplacement.setStartTime(gemakt_starttime);
					}
				}			
    	}
  		
  	}

  	
  	// Sicherstellen, dass die Reihenfolge sortiert nach Index mit der nach Startzeit �bereinstimmt!
  	
  }
  
  
  
  
  /**
   * 
   * F�gt alle gemeinsamen Aktivit�ten, die �ber andere Personen an die aktuelle zugewiesen 
   * worden sind in das Pattern ein bevor die eigentliche Modellierung beginnt
   * 
   */
  private void addJointActivitiestoPattern()
  {
		for (HActivity tmpjointact : person.getAllJointActivitiesforConsideration())
		{
			// Infos f�r die neue, gemeinsame Akt
			int indexday = tmpjointact.getIndexDay();
			int tourindex = tmpjointact.getTour().getIndex();
			
			int personindex_created = tmpjointact.getPerson().getPersIndex();
			
			int activityindex = tmpjointact.getIndex();
			char activitytype = tmpjointact.getType();
			int activityduration = tmpjointact.getDuration();
			int activitystarttime = tmpjointact.getStartTime();
			int activityjointStatus = tmpjointact.getJointStatus();
			int activitytripdurationbefore = tmpjointact.getEstimatedTripTimeBeforeActivity();
			
							
      // Hole Referenz auf Tag und Tour oder erzeuge Sie neu falls noch nicht existent
      HDay currentDay = pattern.getDay(indexday);
      HTour oneTour;
			if (currentDay.existsTour(tourindex))
			{
				oneTour = currentDay.getTour(tourindex);
			}
			else
			{
				oneTour = new HTour(currentDay, tourindex);
			}
                
      // F�ge die Aktivit�t in das Pattern ein
			HActivity activity = null;
			
			
			switch(activityjointStatus)
			{
				// Weg davor und Aktivit�t werden gemeinsam durchgef�hrt
				case 1:
				{
					activity = new HActivity(oneTour, activityindex, activitytype, activityduration, activitystarttime, activityjointStatus, activitytripdurationbefore);
					break;
				}
				// Nur Aktivit�t wird gemeinsam durchgef�hrt
				case 2:
				{
					activity = new HActivity(oneTour, activityindex, activitytype, activityduration, activitystarttime, activityjointStatus);
					break;
				}		
				// Weg davor wird gemeinsam durchgef�hrt
				case 3:
				{
					activity = new HActivity(oneTour, activityindex, activitystarttime, activityjointStatus, activitytripdurationbefore);
					break;
				}
			}			
			assert activity!=null : "Aktivit�t wurde nicht erzeugt";
			activity.addAttributetoMap("CreatorPersonIndex", (double) personindex_created); 
			
			
			/*
			 *  Pr�fe, ob Aktivit�t konfliktfrei einf�gbar ist in das Pattern
			 */
			boolean konfliktfrei = true;
			String reason="";
			String existingaktinvolved="";
			String newaktaktinvolved="";
			
			// Aktivit�t mit diesem Index in dieser Tour existiert bereits
			if (currentDay.existsActivity(oneTour.getIndex(), activityindex)) 
			{
				konfliktfrei=false;
				reason = "Aktivit�t mit Index existiert bereits in Tour!";
				newaktaktinvolved = activity.toString();
			}
			
			// Aktivit�t passt von der Reihenfolge (Index und Zeit) nicht an diese Position
			for (HActivity tmpact : currentDay.getAllActivitiesoftheDay())
			{
				if (
						(tmpact.getTour().getIndex() > activity.getTour().getIndex() && tmpact.getStartTime() < activity.getStartTime())
						||
						(tmpact.getTour().getIndex() == activity.getTour().getIndex() && tmpact.getIndex() > activity.getIndex() && tmpact.getStartTime() < activity.getStartTime())
					)
				{
					konfliktfrei = false;
					reason = "Aktivit�t passt zeitlich nicht an diese Position!";
					existingaktinvolved = tmpact.toString();
					newaktaktinvolved = activity.toString();
					break;
				}
			}
			
			// Zeitraum der Akt kollidiert mit anderer Akt
			for (HActivity tmpact : currentDay.getAllActivitiesoftheDay())
			{
				if (tmpact.startTimeisScheduled() && tmpact.checkOverlappingtoOtherActivity(activity))
				{
					konfliktfrei = false;
					reason = "Aktivit�ten �berlagern sich!";
					existingaktinvolved = tmpact.toString();
					newaktaktinvolved = activity.toString();
					break;
				}
			}								
			
			if (konfliktfrei)
			{
				if (!currentDay.existsTour(tourindex)) currentDay.addTour(oneTour);			
				oneTour.addActivity(activity);
			}
			else
			{
				System.err.println("gemeinsame Aktivit�t konnte nicht eingef�gt werden! // " + reason);
				// System.err.println("Aktiviti�t Tag:" + currentDay.getIndex() + " Tour: " + activity.getTour().getIndex() + " Aktindex: " + activityindex + " Startzeit: " + activitystarttime + "(" + tmpjointact.getStartTimeWeekContext() + ")");
				System.err.println("Existing Conflicting Act: " + existingaktinvolved);
				System.err.println("New Conflicting Act: " + newaktaktinvolved);
			}
		}
  }
  
    

  /**
   * 
   * @param id
   * @param variablenname
   */
	private void executeStep1(String id, String variablenname)
	{
		// AttributeLookup erzeugen
		AttributeLookup lookup = new AttributeLookup(person);
		
    // Step-Objekt erzeugen
    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
    step.doStep();
    
    // Ergebnis zu Personen-Map hinzuf�gen
    person.addAttributetoMap(variablenname, Double.parseDouble(step.getAlternativeChosen()));
	}

	/**
	 * 
	 * @param id
	 */
  private void executeStep2(String id)
  {
    // STEP 2A Main tour and main activity
    for (HDay currentDay : pattern.getDays())
    {
    	// Schritt wird ausgef�hrt, falls die Hauptaktivit�t noch nicht exisitiert oder noch keinen Aktivit�tstyp hat
    	if(!currentDay.existsActivityTypeforActivity(0,0))
    	{
      	// AttributeLookup erzeugen
    		AttributeLookup lookup = new AttributeLookup(person, currentDay);   	
      	
  	    // Step-Objekt erzeugen
  	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
  	    
  	    // Falls es schon Touren gibt (aus gemeinsamen Akt), H als Aktivit�tstyp ausschlie�en
  	    if (currentDay.getAmountOfTours()>0 || numberoftoursperday_lowerboundduetojointactions[currentDay.getIndex()]>0)
  	    {
  	    	step.limitUpperBoundOnly(step.alternatives.size()-2);
	    	}
  	    
  	    // Auswahl durchf�hren
  	    step.doStep();
  	    char activityType = step.getAlternativeChosen().charAt(0);
    		
  	    if (activityType!='H')
        {	
          // F�ge die Tour in das Pattern ein, falls sie noch nicht existiert
  	    	HTour mainTour = null;
  	    	if (!currentDay.existsTour(0))
          {
          	mainTour = new HTour(currentDay, 0);
          	currentDay.addTour(mainTour);
          }
  	    	else
  	    	{
  	    		mainTour = currentDay.getTour(0);
  	    	}
  	    	
  	    	// F�ge die Aktivit�t in das Pattern ein, falls sie noch nicht existiert
  	    	HActivity activity = null;
  	    	if (!currentDay.existsActivity(0,0))
          {
  	    		activity = new HActivity(mainTour, 0, activityType);
            mainTour.addActivity(activity);
          }
  	    	else
  	    	{
  	    		activity = currentDay.getTour(0).getActivity(0);
  	    		activity.setType(activityType);
  	    	}
        }
    	}		    
    }
  }
  
  /**
   * 
   * @param id
   */
	private void executeStep3(String id)
	{
    for (HDay currentDay : pattern.getDays())
    {   	
    	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
      if (currentDay.isHomeDay())
      {
      	continue;
      }
      
      // AttributeLookup erzeugen
  		AttributeLookup lookup = new AttributeLookup(person, currentDay);   	
    	
	    // Step-Objekt erzeugen
	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
	    
	    // Mindesttourzahl festlegen, falls es schon Touren aus gemeinsamen Aktivit�ten gibt
	    int mindesttourzahl=0;
	    
	    // Pr�fe, ob Mindesttourzahl aus lowerBound bereits erreicht wurde
	    if (currentDay.getAmountOfTours() < numberoftoursperday_lowerboundduetojointactions[currentDay.getIndex()])
	    {
	    	int verbleibendetouren = numberoftoursperday_lowerboundduetojointactions[currentDay.getIndex()] - currentDay.getAmountOfTours();
	    	// Bei 3A werden verbleibende Touren halbiert, da auch ggf. noch Touren in 3B modelliert werden k�nnen
	    	if (id.equals("3A")) mindesttourzahl = Math.round(verbleibendetouren/2);
	    	// 3B bekommt als Mindesttourenzahl alle bis dahin noch verbliebenen Touren
	    	if (id.equals("3B")) mindesttourzahl = verbleibendetouren;
	    }
	    
	    // Alternativen limitieren basierend auf Mindestourzahl
	    step.limitLowerBoundOnly(mindesttourzahl);
	    
	    // Entscheidung durchf�hren
	    step.doStep();
            
      // Erstelle die weiteren Touren an diesem Tag basierend auf der Entscheidung und f�ge Sie in das Pattern ein, falls sie noch nicht existieren
      for (int j = 1; j <= step.getDecision(); j++)
      {
      	HTour tour = null;
      	// 3A - Touren vor der Haupttour
        if (id.equals("3A") && !currentDay.existsTour(-1*j)) tour = new HTour(currentDay, (-1) * j);
      	// 3B - Touren nach der Haupttour
        if (id.equals("3B") && !currentDay.existsTour(+1*j)) tour = new HTour(currentDay, (+1) * j);        
        
        if (tour!=null) currentDay.addTour(tour);
      }
    
      HTour.sortTourList(currentDay.getTours());
    
	    if (id.equals("3B")) assert (currentDay.getAmountOfTours() >= numberoftoursperday_lowerboundduetojointactions[currentDay.getIndex()]) : "wrong number of tours - violating lower bound due to joint actions";
    }    
	}

	/**
	 * 
	 * @param id
	 */
  private void executeStep4(String id)
  {
    // STEP 4A Main activity for all other tours
    for (HDay currentDay : pattern.getDays())
    {
    	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
                     
      for (HTour currentTour : currentDay.getTours())
      {
        /*
         * Ignoriere Touren, deren Hauptaktivit�t schon festgelegt ist
         * 	- Hauptouren des Tages (siehe Schritt 2)
         *  - andere Hauptaktivit�ten, welche �ber gemeinsame Aktivit�ten ins Pattern gekommen sind
         */
      	if(!currentDay.existsActivityTypeforActivity(currentTour.getIndex(),0))
        {
        	// AttributeLookup erzeugen
      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
        	
    	    // Step-Objekt erzeugen
    	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
    	    step.doStep();

          // Speichere gew�hlte Entscheidung f�r weitere Verwendung
          char chosenActivityType = step.getAlternativeChosen().charAt(0);
          
  	    	HActivity activity = null;
  	    	
  	    	// Falls die Aktivit�t existiert wird nur deren Typ bestimmt
  	    	if (currentDay.existsActivity(currentTour.getIndex(),0))
          {
  	    		activity = currentTour.getActivity(0);
  	    		activity.setType(chosenActivityType);
          }
  	    	// Erstelle die Aktivit�t mit entsprechendem Typ, falls Sie noch nicht exisitert
  	    	else
  	    	{ 	    		
  	    		activity = new HActivity(currentTour, 0, chosenActivityType);
  	    		currentTour.addActivity(activity);
  	    	}
        }
      }
    }
  }

  /**
   * 
   * @param id
   */
	private void executeStep5(String id)
	{
    for (HDay currentDay : pattern.getDays())
    {
    	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
        
      for (int i = currentDay.getLowestTourIndex(); i <= currentDay.getHighestTourIndex(); i++)
      {
      	HTour currentTour = currentDay.getTour(i);
      	
      	// AttributeLookup erzeugen
    		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
      	
    	  // Step-Objekt erzeugen
  	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
    		
  	    // Mindesaktzahl festlegen, falls es schon Aktivit�ten aus gemeinsamen Aktivit�ten gibt
  	    int mindestaktzahl =0;
  	    
  	    // Pr�fe, ob Mindestaktzahl aus lowerBound bereits erreicht wurde
  	    if (currentDay.getTotalAmountOfActivitites() < numberofactsperday_lowerboundduetojointactions[currentDay.getIndex()])
  	    {
  	    	// Bestimme noch nicht verplante Aktivit�ten basierend auf Mindestzahl aus gemeinsamen Akt und bereits verplanten
  	    	int verbleibendeakt = numberofactsperday_lowerboundduetojointactions[currentDay.getIndex()] - currentDay.getTotalAmountOfActivitites();
  		
  	    	/*
  	    	 * Bestimme, wieviele Aktivit�tensch�tzungen in Schritt 5 noch durchgef�hrt werden
					 *
  	    	 * Formel: verbleibendenAnzahlanTouren * 2 (wegen 5A und 5B) - 1 (falls es schon Schritt 5B ist und damit 5A schon durchgef�hrt wurde
  	    	 * verbleibendeAnzahlanTouren =  currentDay.getHighestTourIndex() - aktuellerTourIndex(i) + 1
  	    	 */
  	    	int verbleibendeaktschaetzungen =  2*(currentDay.getHighestTourIndex() - i + 1) - (id.equals("5B") ? 1 : 0);
  	    	// Bestimme Mindestzahl aufgrund verbleibender Akt im Verh�ltnis zu verbleibenden Sch�tzungen 
  	    	mindestaktzahl = Math.round(verbleibendeakt/verbleibendeaktschaetzungen);
  	    	// bei der letzten Tour des Tages und der NACH-Aktivit�tenzahl muss Mindestanzahl zwingend erreicht werden
  	    	if (id.equals("5B") && currentTour.getIndex() == currentDay.getHighestTourIndex()) mindestaktzahl = verbleibendeakt;
  	    }
  	    
  	    // Alternativen limitieren basierend auf Mindesaktzahl
  	    step.limitLowerBoundOnly(mindestaktzahl);
  	    
  	    // Entscheidung durchf�hren
  	    step.doStep();    		

  	    // Erstelle die weiteren Aktivit�ten in dieser Tour basierend auf der Entscheidung und f�ge Sie in das Pattern ein
        for (int j = 1; j <= step.getDecision(); j++)
        {
        	HActivity act = null;
        	// 5A - Touren vor der Haupttour
          if (id.equals("5A") && !currentDay.existsActivity(currentTour.getIndex(),-1*j)) act = new HActivity(currentTour, (-1) * j);
        	// 5B - Touren nach der Haupttour
          if (id.equals("5B") && !currentDay.existsActivity(currentTour.getIndex(),+1*j)) act = new HActivity(currentTour, (+1) * j);
          
          if (act!=null) currentTour.addActivity(act);
        }
        
        HActivity.sortActivityList(currentTour.getActivities());        
      }
      if (id.equals("5B")) assert (currentDay.getTotalAmountOfActivitites() >= numberofactsperday_lowerboundduetojointactions[currentDay.getIndex()]) : "wrong number of activities - violating lower bound due to joint actions";
    }
	}

	/**
	 * 
	 * @param id
	 */
	private void executeStep6(String id)
	{
    // STEP 6A Non-Main-Activity Type Decision
    for (HDay currentDay : pattern.getDays())
    {
    	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
      
      for (HTour currentTour : currentDay.getTours())
      {
        for (HActivity currentActivity : currentTour.getActivities())
        {
        	// only use activities whose type has not been decided yet
          if (!currentActivity.activitytypeisScheduled())
          {
          	// AttributeLookup erzeugen
        		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, currentActivity);   	
          	
      	    // Step-Objekt erzeugen
      	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
      	    step.doStep();

            // Aktivit�tstyp festlegen
      	    currentActivity.setType(step.getAlternativeChosen().charAt(0));
          }
        }
      }
    }
	}

	/**
	 * 
	 * Festlegung von Default-Wegzeiten f�r alle Aktivit�ten
	 * 
	 */
	private void createTripTimesforActivities() 
	{
	  for (HDay day : pattern.getDays())
	  {
	    for (HTour tour : day.getTours())
	    {
	    	for (HActivity act : tour.getActivities())
	      {
	    		act.calculateAndSetTripTimes();
	      }
	    }
	  }	
	}



	/**
	 * 
	 * @param id
	 * @param variablenname
	 */
	private void executeStep7DC(String id, char activitytype)
	{
		// Wird nur ausgef�hrt, wenn es zu dem Aktivit�tentyp auch Aktivit�ten gibt
	  if (pattern.countActivitiesPerWeek(activitytype)>0)
	  {
			// AttributeLookup erzeugen
			AttributeLookup lookup = new AttributeLookup(person);
			
	    // Step-Objekt erzeugen
	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
	    step.doStep();
	    
	    // Ergebnis als Index und Alternative zu Personen-Map hinzuf�gen f�r sp�tere Verwendung
	    person.addAttributetoMap(activitytype+"budget_category_index", (double) step.getDecision());
	    person.addAttributetoMap(activitytype+"budget_category_alternative", Double.parseDouble(step.getAlternativeChosen()));
	  }
	  
	  // special case: if there is exactly no activity allocated for work, than we must set cat to 0
	  // needed to achieve value for Attribute zeitbudget_work_ueber_kat2
    if (activitytype=='W' && pattern.countActivitiesPerWeek(activitytype)==0)
    {
    	person.addAttributetoMap(activitytype+"budget_category_alternative", 0.0d);
    } 
	}

	/**
	 * 
	 * @param id
	 * @param activitytype
	 */
	private void executeStep7MC(String id, char activitytype)
    {
	  	// Wird nur ausgef�hrt, wenn es zu dem Aktivit�tentyp auch Aktivit�ten gibt
	  	if (pattern.countActivitiesPerWeek(activitytype)>0)
      {
        // Entscheidung aus Schritt 7A-E ermitteln
        double chosenIndex = person.getAttributefromMap(activitytype+"budget_category_index");

        DefaultMCModelStep step = new DefaultMCModelStep(id + (int) chosenIndex, this);
        step.doStep();
        
        int chosenTime = step.getChosenTime();
        //Entscheidungsindex als Property speichern
        person.addAttributetoMap(activitytype+"budget_exact",(double) chosenTime);
      }
    }	
	
	/**
	 * 
	 * @param id
	 */
	private void executeStep8A(String id) 
	{
    // STEP8a: yes/no decision for "activity is in average time class xyz".
    // only applies to main activities
    for (HDay currentDay : pattern.getDays())
    {
    	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
  	
      for (HTour currentTour : currentDay.getTours())
      {
      	// Anwendung des Modellschritts nur auf Hauptaktivit�ten
        HActivity currentActivity = currentTour.getActivity(0);
        
        // Schritt wird nur durchgef�hrt, falls Dauer der Aktivit�t noch nicht feststeht
        if(!currentActivity.durationisScheduled())
        {
        	// AttributeLookup erzeugen
      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, currentActivity);   	
        	
    	    // Step-Objekt erzeugen
    	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
    	    step.doStep();

    	    // Eigenschaft abspeichern
    	    currentActivity.addAttributetoMap("standarddauer",(step.getAlternativeChosen().equals("yes") ? 1.0d : 0.0d));
    	    
    	    // Bei unkoordinierter Modellierung ohne Stabilit�tsaspekte wird der Wert immer mit 0 �berschrieben!
    	    if (!Configuration.coordinated_modelling) currentActivity.addAttributetoMap("standarddauer", 0.0d);
        }
      }
    }
	}


	/**
	 * 
	 * @param id_dc
	 * @param id_mc
	 */
	private void executeStep8_MainAct(String id_dc, String id_mc) throws InvalidPersonPatternException, InvalidHouseholdPatternException
	{
		
		// Modifizierte Zeitverteilungen zur Modellierung von h�heren Auswahlwahrscheinlichkeiten bereits gew�hlter Zeiten
	  modifiedActDurationDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_ACT_DURATION_CLASSES];
		
	  for (HDay currentDay : pattern.getDays())
	  {
	  	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
	  	if (currentDay.isHomeDay())
	    {
	    	continue;
	    }
		
	  	// Anwendung des Modellschritts nur auf Hauptaktivit�ten
			for (HTour currentTour : currentDay.getTours())
			{
				boolean running=false;
				if (id_dc.equals("8B") && currentTour.getIndex()==0) running=true;  // 8B gilt nur f�r Haupttouren (TourIndex=0)
				if (id_dc.equals("8D") && currentTour.getIndex()!=0) running=true;	// 8D gilt nur f�r NICHT-Haupttouren (TourIndex!=0)
					
				if (running)
				{
	        HActivity currentActivity = currentTour.getActivity(0);
	        
	  	    /*
	  	     * 
	  	     * DC-Schritt (8B, 8D)
	  	     * 
	  	     */
	        // Schritt nur durchf�hren, falls Dauer noch nicht festgelegt wurde
	        if (!currentActivity.durationisScheduled())
	        {
	          // AttributeLookup erzeugen
	      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, currentActivity);   	
	        	
	    	    // Step-Objekt erzeugen
	    	    DefaultDCModelStep step_dc = new DefaultDCModelStep(id_dc, this, lookup);
	    	    
	    	    // Alternativen ggf. auf Standardzeitkategorie einschr�nken
	    	    modifyAlternativesDueTo8A(currentActivity, step_dc);  	    
	    	    
	    	    // Alternativen ggf. basierend auf bereits festgelgten Dauern beschr�nken
	    	    int maxduration = calculateMaxdurationDueToScheduledActivities(currentActivity);
	    	    int loc_upperbound = getDurationTimeClassforExactDuration(maxduration);
	    	    
	    	    if (loc_upperbound <= step_dc.getUpperBound() || step_dc.getUpperBound()==-1) step_dc.limitUpperBoundOnly(loc_upperbound); 
	    	    if (loc_upperbound <= step_dc.getLowerBound()) 																step_dc.limitLowerBoundOnly(loc_upperbound); 
	
	    	    // Wahlentscheidung durchf�hren
	    	    step_dc.doStep();
	
	    	    // Entscheidungsindex abspeichern
	    	    currentActivity.addAttributetoMap("actdurcat_index",(double) step_dc.getDecision()); 	
	    	    
	    	    /*
	    	     * 
	    	     * MC-Schritt (8C, 8E)
	    	     * 
	    	     */
	    	    // Schritt nur durchf�hren, falls Dauer noch nicht festgelegt wurde
	          if (!currentActivity.durationisScheduled())
		        {
	          	// Objekt basierend auf der gew�hlten Zeitkategorie initialisieren
				      double chosenTimeCategory = currentActivity.getAttributesMap().get("actdurcat_index");
				      DefaultMCModelStep step_mc = new DefaultMCModelStep(id_mc + (int) chosenTimeCategory, this);
				      step_mc.setModifiedDTDtoUse(currentActivity.getType(), (int) chosenTimeCategory);
				      
				      // angepasste Zeitverteilungen aus vorherigen Entscheidungen werden nur im koordinierten Fall verwendet
				      step_mc.setModifyDTDAfterStep(Configuration.coordinated_modelling);
				      step_mc.setDTDTypeToUse(INDICATOR_ACT_DURATIONS);
				      
				      // Limitiere die Obergrenze durch die noch verf�gbare Zeit
				      step_mc.setRangeBounds(0, calculateMaxdurationDueToScheduledActivities(currentActivity));
				      
				      // Wahlentscheidung durchf�hren
				      step_mc.doStep();
				     
				      // Speichere Ergebnisse ab
				      currentActivity.setDuration(step_mc.getChosenTime());
		        } 
	        }
				}		
			}
	  }
	}



	/**
	 * 
	 * @param id_dc
	 * @param id_mc
	 */
	private void executeStep8_NonMainAct(String id_dc, String id_mc) throws InvalidPersonPatternException, InvalidHouseholdPatternException
	{
		
		// Modifizierte Zeitverteilungen zur Modellierung von h�heren Auswahlwahrscheinlichkeiten bereits gew�hlter Zeiten
	  modifiedActDurationDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_ACT_DURATION_CLASSES];
		
	  for (HDay currentDay : pattern.getDays())
	  {
	  	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
	  	if (currentDay.isHomeDay())
	    {
	    	continue;
	    }
		
	  	for (HTour currentTour : currentDay.getTours())
	    {
	      for (HActivity currentActivity : currentTour.getActivities())
	      {
	  	    /*
	  	     * 
	  	     * DC-Schritt
	  	     * 
	  	     */
	      	// Schritt nur durchf�hren, falls keine Hauptaktivit�t und Dauer noch nicht festgelegt wurde
	        if (currentActivity.getIndex() != 0 && !currentActivity.durationisScheduled())
	        {   	     
	          // AttributeLookup erzeugen
	      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, currentActivity);   	
	        	
	    	    // Step-Objekt erzeugen
	    	    DefaultDCModelStep step_dc = new DefaultDCModelStep(id_dc, this, lookup);
	    	   
	    	    // Alternativen ggf. basierend auf bereits festgelgten Dauern beschr�nken
	    	    int maxduration = calculateMaxdurationDueToScheduledActivities(currentActivity);
	    	    int loc_upperbound = getDurationTimeClassforExactDuration(maxduration);
	    		   
	    	    if (loc_upperbound <= step_dc.getUpperBound() || step_dc.getUpperBound()==-1) step_dc.limitUpperBoundOnly(loc_upperbound); 
	    	    if (loc_upperbound <= step_dc.getLowerBound()) 																step_dc.limitLowerBoundOnly(loc_upperbound); 
	
	    	    // Wahlentscheidung durchf�hren
	    	    step_dc.doStep();
	
	    	    // Entscheidungsindex abspeichern
	    	    currentActivity.addAttributetoMap("actdurcat_index",(double) step_dc.getDecision()); 	
	    	    
	    	    /*
	    	     * 
	    	     * MC-Schritt
	    	     * 
	    	     */
	    	    // Schritt nur durchf�hren, falls Dauer noch nicht festgelegt wurde
	    	    if (currentActivity.getIndex() != 0 && !currentActivity.durationisScheduled())
	          {
	          	// Objekt basierend auf der gew�hlten Zeitkategorie initialisieren
	          	double chosenTimeCategory = currentActivity.getAttributesMap().get("actdurcat_index");
	  		      DefaultMCModelStep step_mc = new DefaultMCModelStep(id_mc + (int) chosenTimeCategory, this);
	  		      step_mc.setModifiedDTDtoUse(currentActivity.getType(), (int) chosenTimeCategory);
	  		      
	  		      // angepasste Zeitverteilungen aus vorherigen Entscheidungen werden nur im koordinierten Fall verwendet
	  		      step_mc.setModifyDTDAfterStep(Configuration.coordinated_modelling);
	  		      step_mc.setDTDTypeToUse(INDICATOR_ACT_DURATIONS);
	
				      // Limitiere die Obergrenze durch die noch verf�gbare Zeit
	  		      step_mc.setRangeBounds(0, calculateMaxdurationDueToScheduledActivities(currentActivity));
				      
				      // Wahlentscheidung durchf�hren
	  		      step_mc.doStep();
	  		     
	  		      // Speichere Ergebnisse ab
	  		      currentActivity.setDuration(step_mc.getChosenTime());
	          }
	        }
				}		
			}
	  }
	}



	/**
	 * 
	 * @param id
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep8_MainAct_DC(String id)
	{
		for (HDay currentDay : pattern.getDays())
    {
    	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
  	
    	// Anwendung des Modellschritts nur auf Hauptaktivit�ten
  		for (HTour currentTour : currentDay.getTours())
  		{
  			boolean running=false;
  			if (id.equals("8B") && currentTour.getIndex()==0) running=true; // 8B gilt nur f�r Haupttouren (TourIndex=0)
  			if (id.equals("8D") && currentTour.getIndex()!=0) running=true;	// 8D gilt nur f�r NICHT-Haupttouren (TourIndex!=0)
  				
  			if (running)
  			{
          HActivity currentActivity = currentTour.getActivity(0);
          
          // Schritt nur durchf�hren, falls Dauer noch nicht festgelegt wurde
          if (!currentActivity.durationisScheduled())
	        {
	          // AttributeLookup erzeugen
	      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, currentActivity);   	
	        	
	    	    // Step-Objekt erzeugen
	    	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
	    	    
	    	    // Alternativen ggf. auf Standardzeitkategorie einschr�nken
	    	    modifyAlternativesDueTo8A(currentActivity, step);  	    
	    	    
	    	    // Alternativen ggf. basierend auf bereits festgelgten Dauern beschr�nken
	    	    int loc_upperbound = calculateUpperBoundDurationTimeClassDueToPlannedDurations(currentDay);
	    	    	    	    
	    	    if (loc_upperbound <= step.getUpperBound()) step.limitUpperBoundOnly(loc_upperbound); 
	    	    if (loc_upperbound <= step.getLowerBound()) step.limitLowerBoundOnly(loc_upperbound); 

	    	    // Wahlentscheidung durchf�hren
	    	    step.doStep();
	
	    	    // Entscheidungsindex abspeichern
	    	    currentActivity.addAttributetoMap("actdurcat_index",(double) step.getDecision()); 	
	        }
  			}		
  		}
    }			
  }

	/**
	 * 
	 * @param id
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep8_MainAct_MC(String id)
	{
		// Modifizierte Zeitverteilungen zur Modellierung von h�heren Auswahlwahrscheinlichkeiten bereits gew�hlter Zeiten
    modifiedActDurationDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_ACT_DURATION_CLASSES];
    for (HDay currentDay : pattern.getDays())
    {
      if (currentDay.isHomeDay())
      {
      	continue;
      }
      
      // Anwendung des Modellschritts nur auf Hauptaktivit�ten
      for (HTour currentTour : currentDay.getTours())
  		{
      	boolean running=false;
  			if (id.equals("8C") && currentTour.getIndex()==0) running=true;
  			if (id.equals("8E") && currentTour.getIndex()!=0) running=true;
  				
  			if (running)
  			{
		      HActivity currentActivity = currentTour.getActivity(0);
		    
          // Schritt nur durchf�hren, falls Dauer noch nicht festgelegt wurde
          if (!currentActivity.durationisScheduled())
	        {
          	// Objekt basierend auf der gew�hlten Zeitkategorie initialisieren
			      double chosenTimeCategory = currentActivity.getAttributesMap().get("actdurcat_index");
			      DefaultMCModelStep step = new DefaultMCModelStep(id + (int) chosenTimeCategory, this);
			      step.setModifiedDTDtoUse(currentActivity.getType(), (int) chosenTimeCategory);
			      
			      // angepasste Zeitverteilungen aus vorherigen Entscheidungen werden nur im koordinierten Fall verwendet
			      step.setModifyDTDAfterStep(Configuration.coordinated_modelling);
			      step.setDTDTypeToUse(INDICATOR_ACT_DURATIONS);
			      
			      // Limitiere die Obergrenze durch die noch verf�gbare Zeit
			      step.setRangeBounds(0, 1440 - (currentDay.getTotalAmountOfActivityTime() + currentDay.getTotalAmountOfTripTime()));
			      
			      // Wahlentscheidung durchf�hren
			      step.doStep();
			     
			      // Speichere Ergebnisse ab
			      currentActivity.setDuration(step.getChosenTime());
	        }
  			}
  		}
    }
	}
	
	
	
	/**
	 * 
	 * @param id
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep8_NonMainAct_DC(String id)
	{
		// STEP8J: TIME CLASS Step for OTHER activities
	  for (HDay currentDay : pattern.getDays())
	  {
      if (currentDay.isHomeDay())
      {
      	continue;
      }
      for (HTour currentTour : currentDay.getTours())
      {
        for (HActivity currentActivity : currentTour.getActivities())
        {
        	// Schritt nur durchf�hren, falls keine Hauptaktivit�t und Dauer noch nicht festgelegt wurde
          if (currentActivity.getIndex() != 0 && !currentActivity.durationisScheduled())
          {
          	 // AttributeLookup erzeugen
        		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, currentActivity);   	
          	
      	    // Step-Objekt erzeugen
      	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
      	    
      	    // Alternativen ggf. basierend auf bereits festgelgten Dauern beschr�nken
	    	    int loc_upperbound = calculateUpperBoundDurationTimeClassDueToPlannedDurations(currentDay);
	    	    if (loc_upperbound <= step.getUpperBound()) step.limitUpperBoundOnly(loc_upperbound); 
	    	    if (loc_upperbound <= step.getLowerBound()) step.limitLowerBoundOnly(loc_upperbound); 
	    	    
      	    step.doStep();

      	    // Entscheidungsindex abspeichern
      	    currentActivity.addAttributetoMap("actdurcat_index",(double) step.getDecision()); 	          
          }
        }
      }
    }
	}

	/**
	 * 
	 * @param id
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep8_NonMainAct_MC(String id)
	{
		// STEP 8K, determine other activities exact duration
		// Modifizierte Zeitverteilungen zur Modellierung von h�heren Auswahlwahrscheinlichkeiten bereits gew�hlter Zeiten
    modifiedActDurationDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_ACT_DURATION_CLASSES];
    for (HDay currentDay : pattern.getDays())
    {
      if (currentDay.isHomeDay())
      {
      	continue;
      }
      for (HTour currentTour : currentDay.getTours())
      {
        for (HActivity currentActivity : currentTour.getActivities())
        {
        	// Schritt nur durchf�hren, falls keine Hauptaktivit�t und Dauer noch nicht festgelegt wurde
          if (currentActivity.getIndex() != 0 && !currentActivity.durationisScheduled())
          {
          	// Objekt basierend auf der gew�hlten Zeitkategorie initialisieren
          	double chosenTimeCategory = currentActivity.getAttributesMap().get("actdurcat_index");
  		      DefaultMCModelStep step = new DefaultMCModelStep(id + (int) chosenTimeCategory, this);
  		      step.setModifiedDTDtoUse(currentActivity.getType(), (int) chosenTimeCategory);
  		      
  		      // angepasste Zeitverteilungen aus vorherigen Entscheidungen werden nur im koordinierten Fall verwendet
  		      step.setModifyDTDAfterStep(Configuration.coordinated_modelling);
  		      step.setDTDTypeToUse(INDICATOR_ACT_DURATIONS);

			      // Limitiere die Obergrenze durch die noch verf�gbare Zeit
			      step.setRangeBounds(0, 1440 - (currentDay.getTotalAmountOfActivityTime() + currentDay.getTotalAmountOfTripTime()));
			      
			      // Wahlentscheidung durchf�hren
			      step.doStep();
  		     
  		      // Speichere Ergebnisse ab
  		      currentActivity.setDuration(step.getChosenTime());
          }
        }
      }
	  }
	}


	/**
	 * 
	 * @param id
	 */
	private void executeStep9A(String id)
	{
    // Step 9A: standard start time category for main tours during the week
    	
    if (person.isPersonWorkerAndWorkMainToursAreScheduled())
    {
    	 // AttributeLookup erzeugen
  		AttributeLookup lookup = new AttributeLookup(person);   	
    	
	    // Step-Objekt erzeugen
	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
	    step.doStep();

	    // Eigenschaft abspeichern
	    person.addAttributetoMap("main_tours_default_start_cat",(double) step.getDecision());
	   }
	}
	
	

	/**
	 * 
	 * @param id
	 */
	private void executeStep10A(String id)
	{
    // Step 10a: check if main tour for work/edu lies within standard start time (applies only to work/edu persons)
    if (person.isPersonWorkerAndWorkMainToursAreScheduled())
    {
	    for (HDay currentDay : pattern.getDays())
	    {
	      if (currentDay.isHomeDay())
	      {
	      	continue;
	      }
	      
	      // Bestimme Haupttour und deren Tourtyp
	      HTour currentTour = currentDay.getTour(0);
      	char tourtype = currentTour.getActivity(0).getType();
        if (tourtype == 'W' || tourtype == 'E')
        {
        	// AttributeLookup erzeugen
      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
        	
    	    // Step-Objekt erzeugen
    	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
    	    step.doStep();

    	    // Eigenschaft abspeichern
    	    currentTour.addAttributetoMap("default_start_cat_yes",(step.getAlternativeChosen().equals("yes") ? 1.0d : 0.0d));
        }
      }
    }
	}


	/**
	 * 
	 * @param id
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10B(String id) throws InvalidPersonPatternException
	{
	  // STEP 10b: determine time class for the start of a work/edu tour
		modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];
	    if (person.isPersonWorkerAndWorkMainToursAreScheduled())
	    {
	      for (HDay currentDay : pattern.getDays())
	      {
		      if (currentDay.isHomeDay())
		      {
		      	continue;
		      }
		      // Bestimme Haupttour
		      HTour currentTour = currentDay.getTour(0);
		    	char tourtype = currentTour.getActivity(0).getType();
		    	if (tourtype == 'W' || tourtype == 'E')
		      {
		    		// AttributeLookup erzeugen
		    		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
		      	
		  	    // Step-Objekt erzeugen
		  	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
		  	     		
		        // Bestimme Ober- und Untergrenze und schr�nke Alternativenmenge ein
		  	    modifyAlternativesForStep10B(currentDay, currentTour, step);
		  	    
		  	    // F�hre Entscheidungswahl durch
		  	    step.doStep();

		  	    // Eigenschaft abspeichern
		  	    currentTour.addAttributetoMap("tourStartCat_index",(double) step.getDecision());
		      }
	      }
	    }
	}

	/**
	 * 
	 * @param id
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10C(String id) throws  InvalidPersonPatternException
	{
	    // Step 10c: exact start time for the work/edu tour
	    if (person.isPersonWorkerAndWorkMainToursAreScheduled())
	    {
        for (HDay currentDay : pattern.getDays())
        {

        	if (currentDay.isHomeDay())
	        {
	        	continue;
	        }
        	// Bestimme Haupttour
          HTour currentTour = currentDay.getTour(0);
        	char tourtype = currentTour.getActivity(0).getType();
        	if (tourtype == 'W' || tourtype == 'E')
          {
        		// Ermittle Entscheidung aus Schritt DC-Modellschritt  		
            double chosenStartCategory = (double) currentTour.getAttributesMap().get("tourStartCat_index");
            
            // Vorbereitungen und Objekte erzeugen
            String stepID = id + (int) chosenStartCategory;
            DefaultMCModelStep step = new DefaultMCModelStep(stepID, this);
            char mainActivityTypeInTour = currentTour.getActivity(0).getType();
            step.setModifiedDTDtoUse(mainActivityTypeInTour, (int) chosenStartCategory);
            step.setModifyDTDAfterStep(true);
            //step.setOutPropertyName("tourStartTime");
            step.setDTDTypeToUse(INDICATOR_TOUR_STARTTIMES);
            int[] bounds = calculateStartingBoundsForTours(currentDay, currentTour, false);
            step.setRangeBounds(bounds[0], bounds[1]);
            
            // Entscheidung durchf�hren
            step.doStep();
            
            // Speichere Ergebnisse ab
            int chosenStartTime = step.getChosenTime();
            currentTour.setStartTime(chosenStartTime);   
          }
        }
	    }
	}

	/**
	 * 
	 * @param id
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10D(String id) throws InvalidPersonPatternException
	{
	    // STEP 10d: determine time class for the start of all other main tours
		for (HDay currentDay : pattern.getDays())
    {
      if (currentDay.isHomeDay())
      {
      	continue;
      }
    	
      // Bestimme Haupttour
      HTour currentTour = currentDay.getTour(0);
    	
    	// F�hre Schritt nur f�r Haupttouren aus, die noch keine festgelegte Startzeit haben
    	if (!currentTour.isScheduled())
      {
    		// AttributeLookup erzeugen
    		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
      	
  	    // Step-Objekt erzeugen
  	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
  	     		
        // Bestimme Ober- und Untergrenze und schr�nke Alternativenmenge ein
  	    int bounds[] = calculateStartingBoundsForMainTours(currentDay, true);
  	    int lowerbound = bounds[0];
  	    int upperbound = bounds[1];
  	    step.limitAlternatives(lowerbound, upperbound);
  	    
  	    // F�hre Entscheidungswahl durch
  	    step.doStep();

  	    // Eigenschaft abspeichern
  	    currentTour.addAttributetoMap("tourStartCat_index",(double) step.getDecision());
      }
    }
	}

	/**
	 * 
	 * @param id
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10E(String id) throws InvalidPersonPatternException
	{
		// Step 10e: exact start time for other main tours
		modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];
    for (HDay currentDay : pattern.getDays())
    {
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
    	
      // Bestimme Haupttour
      HTour currentTour = currentDay.getTour(0);
    	
    	// F�hre Schritt nur f�r Haupttouren aus, die noch keine festgelegte Startzeit haben
    	if (!currentTour.isScheduled())
      {
    		// Ermittle Entscheidung aus Schritt DC-Modellschritt  		
        double chosenStartCategory = (double) currentTour.getAttributesMap().get("tourStartCat_index");
        
        // Vorbereitungen und Objekte erzeugen
        String stepID = id + (int) chosenStartCategory;
        DefaultMCModelStep step = new DefaultMCModelStep(stepID, this);
        char mainActivityTypeInTour = currentTour.getActivity(0).getType();
        step.setModifiedDTDtoUse(mainActivityTypeInTour, (int) chosenStartCategory);
        step.setModifyDTDAfterStep(true);
        //step.setOutPropertyName("tourStartTime");
        step.setDTDTypeToUse(INDICATOR_TOUR_STARTTIMES);
        int[] bounds = calculateStartingBoundsForTours(currentDay, currentTour, false);
        step.setRangeBounds(bounds[0], bounds[1]);
        
        // Entscheidung durchf�hren
        step.doStep();
        
        // Speichere Ergebnisse ab
        int chosenStartTime = step.getChosenTime();
        currentTour.setStartTime(chosenStartTime);   
      }
    }
  }	


	/**
	 * 
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10GH() throws InvalidPersonPatternException
	{
    // Step 10g and Step10h: determine start time class for tours PRIOR to main tour and determine the exact start time
    // the tours MUST be picked in a certain order: first one (earliest tour) first.

    // reset tour start dtds
    modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];
    for (HDay currentDay : pattern.getDays())
    {
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
      for (int j=currentDay.getLowestTourIndex(); j<0; j++)
      {
      	HTour currentTour = currentDay.getTour(j);
      	
      	// Wenn noch keine Startzeit festgelegt wurde und die Tour vor der Haupttour liegt (Index <0)
        if (!currentTour.isScheduled())
        {
        	// 10G
        	
	      		// AttributeLookup erzeugen
	      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
	        	
	    	    // Step-Objekt erzeugen
	    	    DefaultDCModelStep dcstep = new DefaultDCModelStep("10G", this, lookup);
	    	     		
	          // Bestimme Ober- und Untergrenze und schr�nke Alternativenmenge ein
	    	    int dcbounds[] = calculateStartingBoundsForPreTours(currentDay, currentTour, true);
	    	    int lowerbound = dcbounds[0];
	    	    int upperbound = dcbounds[1];
	    	    dcstep.limitAlternatives(lowerbound, upperbound);
	    	    
	    	    // F�hre Entscheidungswahl durch
	    	    dcstep.doStep();
	
	    	    // Eigenschaft abspeichern
	    	    int chosenStartCategory = dcstep.getDecision();
        	
	    	  // 10H
	    	    
	    	    // Vorbereitungen und Objekte erzeugen
	          String stepID = "10H" + (int) chosenStartCategory;
	          DefaultMCModelStep mcstep = new DefaultMCModelStep(stepID, this);
	          char mainActivityTypeInTour = currentTour.getActivity(0).getType();
	          mcstep.setModifiedDTDtoUse(mainActivityTypeInTour, (int) chosenStartCategory);
	          mcstep.setModifyDTDAfterStep(true);
	          mcstep.setDTDTypeToUse(INDICATOR_TOUR_STARTTIMES);
	          int[] mcbounds = calculateStartingBoundsForPreTours(currentDay, currentTour, false);
	          mcstep.setRangeBounds(mcbounds[0], mcbounds[1]);
	          
	          // Entscheidung durchf�hren
	          mcstep.doStep();
	          
	          // Speichere Ergebnisse ab
	          int chosenStartTime = mcstep.getChosenTime();
	          currentTour.setStartTime(chosenStartTime);   
        }
      }
    }
	}

	
	/**
	 * 
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10JK() throws InvalidPersonPatternException
  {
  	// Step 10j and Step 10k: determine start time class for tours POST to main tour and determine the exact start time
    // the tours MUST be picked in a certain order: first one after main tour first.

    // reset tour start dtds
    modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];
    for (HDay currentDay : pattern.getDays())
    {
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
      for (int j=1; j<=currentDay.getHighestTourIndex(); j++)
      {
      	HTour currentTour = currentDay.getTour(j);
      	// Wenn noch keine Startzeit festgelegt wurde
      	if (!currentTour.isScheduled())
        {          	
        	// 10J
    		
	      		// AttributeLookup erzeugen
	      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
	        	
	    	    // Step-Objekt erzeugen
	    	    DefaultDCModelStep dcstep = new DefaultDCModelStep("10J", this, lookup);
	    	     		
	          // Bestimme Ober- und Untergrenze und schr�nke Alternativenmenge ein
	    	    int dcbounds[] = calculateStartingBoundsForPostTours(currentDay, currentTour, true);
	    	    int lowerbound = dcbounds[0];
	    	    int upperbound = dcbounds[1];
	    	    dcstep.limitAlternatives(lowerbound, upperbound);
	    	    
	    	    // F�hre Entscheidungswahl durch
	    	    dcstep.doStep();
	
	    	    // Eigenschaft abspeichern
	    	    int chosenStartCategory = dcstep.getDecision();

	    	  // 10K
	    	    
	    	    // Vorbereitungen und Objekte erzeugen
	          String stepID = "10K" + (int) chosenStartCategory;
	          DefaultMCModelStep mcstep = new DefaultMCModelStep(stepID, this);
	          char mainActivityTypeInTour = currentTour.getActivity(0).getType();
	          mcstep.setModifiedDTDtoUse(mainActivityTypeInTour, (int) chosenStartCategory);
	          mcstep.setModifyDTDAfterStep(true);
	          mcstep.setDTDTypeToUse(INDICATOR_TOUR_STARTTIMES);
	          int[] mcbounds = calculateStartingBoundsForPostTours(currentDay, currentTour, false);
	          mcstep.setRangeBounds(mcbounds[0], mcbounds[1]);
	          
	          // Entscheidung durchf�hren
	          mcstep.doStep();
	          
	          // Speichere Ergebnisse ab
	          int chosenStartTime = mcstep.getChosenTime();
	          currentTour.setStartTime(chosenStartTime);   
        }
      }
    } 	
  }

	/**
	 * 
	 * Legt die Startzeiten f�r Touren fest bei denen es bereits festgelegte Startzeiten f�r Aktivit�ten gibt, 
	 * bspw. durch bereits festgelegte gemeinsame Aktivit�ten von anderen Personen
	 * 
	 */
	private void createTourStartTimesDueToScheduledActivities()
	{
		for (HDay currentDay : pattern.getDays())
	  {
			// Falls zu wenig Touren oder ein Heimtag vorliegt, wird der Tag �bersprungen
	    if (currentDay.isHomeDay())
	    {
	    	continue;
	    }
	  	
	    for (HTour currentTour : currentDay.getTours())
	    {
		  	// F�hre Schritt nur f�r Touren aus, die noch keine festgelegte Startzeit haben
		  	if (!currentTour.isScheduled())
		    {
		  		
		  		// Pr�fe, ob es eine Aktivit�t in der Tour gibt, deren Startzeit bereits festgelegt wurde (bspw. durch gemeinsame Aktivit�ten)
		  		int startTimeDueToScheduledActivities=-1;
		  		
	  			int tripdurations=0;
	  			int activitydurations=0;
	  			
		  		HActivity.sortActivityList(currentTour.getActivities());
		  		for (HActivity tmpact : currentTour.getActivities())
		  		{
		  			/*
		  			 *  Wenn die Startzeit der Aktivit�t festgelegt ist, rechne von dem Punkt aus 
		  			 *  r�ckw�rts und ziehe alle Dauern bisheriger Wege und Aktivit�ten in der Tour ab
		  			 */		  			
		  			if (tmpact.startTimeisScheduled())
		  			{
		  				startTimeDueToScheduledActivities= tmpact.getTripStartTimeBeforeActivity() - tripdurations - activitydurations;
		  				break;
		  			}
		  			/*
		  			 * Andernfalls addiere die Tour und Aktivit�tszeit auf
		  			 */
		  			else
		  			{
		  				tripdurations += tmpact.getEstimatedTripTimeBeforeActivity();
		  				activitydurations += tmpact.getDuration();
		  			}
		  		}
		  		
		  		// Lege Startzeit fest falls durch bereits festgelegte Aktivit�ten bestimmt 
		  		if (startTimeDueToScheduledActivities!=-1)
		  		{
		  			// Startzeit der Tour festlegen
		  			currentTour.setStartTime(startTimeDueToScheduledActivities);   
		  			// Setze die Startzeiten der Aktivit�ten in dieser Tour
		        createStartTimesforActivities(currentTour);
		  		}
		    }
	    }
	  }
	}

  /**
	 * 
	 * @param id
	 * @param tournrdestages
	 * @throws InvalidPersonPatternException
	 */
	private void executeStep10(String id_dc, String id_mc, int tournrdestages) throws InvalidPersonPatternException
	{
		// Step 10: exact start time for x tour of the day
		modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];
			
	  // STEP 10: determine time class for the start of the x tour of the day
		for (HDay currentDay : pattern.getDays())
	  {
			// Falls zu wenig Touren oder ein Heimtag vorliegt, wird der Tag �bersprungen
	    if (currentDay.isHomeDay()|| currentDay.getAmountOfTours()<tournrdestages)
	    {
	    	continue;
	    }
	  	
	    // Bestimme x-te Tour des Tages
	    HTour currentTour = currentDay.getTour(currentDay.getLowestTourIndex()+(tournrdestages-1));
	  	
	  	// F�hre Schritt nur f�r Touren aus, die noch keine festgelegte Startzeit haben
	  	if (!currentTour.isScheduled())
	    {
  		
  			/*
  			 * 
  			 * DC-Schritt
  			 * 
  			 */
  		
    		// AttributeLookup erzeugen
    		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
      	
  	    // Step-Objekt erzeugen
  	    DefaultDCModelStep step_dc = new DefaultDCModelStep(id_dc, this, lookup);
  	     		
        // Bestimme Ober- und Untergrenze und schr�nke Alternativenmenge ein
        int bounds_dc[] = calculateStartingBoundsForTours(currentTour, true);
  	    int lowerbound = bounds_dc[0];
  	    int upperbound = bounds_dc[1];
  	    step_dc.limitAlternatives(lowerbound, upperbound);
  	    
  	    // F�hre Entscheidungswahl durch
  	    step_dc.doStep();

  	    // Eigenschaft abspeichern
  	    currentTour.addAttributetoMap("tourStartCat_index",(double) step_dc.getDecision());
  	    
  	    
  	    /*
  	     * 
  	     * MC-Schritt
  	     * 
  	     */
  	    
  	    // Ermittle Entscheidung aus Schritt DC-Modellschritt  		
        double chosenStartCategory = (double) currentTour.getAttributesMap().get("tourStartCat_index");
        
        // Vorbereitungen und Objekte erzeugen
        String stepID = id_mc + (int) chosenStartCategory;
        DefaultMCModelStep step_mc = new DefaultMCModelStep(stepID, this);
        char mainActivityTypeInTour = currentTour.getActivity(0).getType();
        step_mc.setModifiedDTDtoUse(mainActivityTypeInTour, (int) chosenStartCategory);
        // angepasste Zeitverteilungen aus vorherigen Entscheidungen werden nur im koordinierten Fall verwendet
        step_mc.setModifyDTDAfterStep(Configuration.coordinated_modelling);
        //step.setOutPropertyName("tourStartTime");
        step_mc.setDTDTypeToUse(INDICATOR_TOUR_STARTTIMES);
        int[] bounds_mc = calculateStartingBoundsForTours(currentTour, false);
        step_mc.setRangeBounds(bounds_mc[0], bounds_mc[1]);
        
        // Entscheidung durchf�hren
        step_mc.doStep();
        
        // Speichere Ergebnisse ab
        int chosenStartTime = step_mc.getChosenTime();
        currentTour.setStartTime(chosenStartTime);   	  	
        
        // Setze die Startzeiten der Aktivit�ten in dieser Tour
        createStartTimesforActivities(currentTour);
		  }	       
	  }
	}



	/**
	 * 
	 * @param id
	 * @param tournrdestages
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10DC(String id, int tournrdestages) throws InvalidPersonPatternException
	{
	  // STEP 10m: determine time class for the start of the x tour of the day
		for (HDay currentDay : pattern.getDays())
	  {
			// Falls zu wenig Touren oder ein Heimtag vorliegt, wird der Tag �bersprungen
	    if (currentDay.isHomeDay()|| currentDay.getAmountOfTours()<tournrdestages)
	    {
	    	continue;
	    }
	  	
	    // Bestimme x-te Tour des Tages
	    HTour currentTour = currentDay.getTour(currentDay.getLowestTourIndex()+(tournrdestages-1));
	  	
	  	// F�hre Schritt nur f�r Touren aus, die noch keine festgelegte Startzeit haben
	  	if (!currentTour.isScheduled())
	    {
	  		// AttributeLookup erzeugen
	  		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
	    	
		    // Step-Objekt erzeugen
		    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
		     		
	      // Bestimme Ober- und Untergrenze und schr�nke Alternativenmenge ein
	      int bounds[] = calculateStartingBoundsForTours(currentDay, currentTour, true);
		    int lowerbound = bounds[0];
		    int upperbound = bounds[1];
		    step.limitAlternatives(lowerbound, upperbound);
		    
		    // F�hre Entscheidungswahl durch
		    step.doStep();
	
		    // Eigenschaft abspeichern
		    currentTour.addAttributetoMap("tourStartCat_index",(double) step.getDecision());
		  }	       
	  }
	}



	/**
	 * 
	 * @param id
	 * @param tournrdestages
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void executeStep10MC(String id, int tournrdestages) throws InvalidPersonPatternException
	{
		// Step 10n: exact start time for x tour of the day
		modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];
    
		for (HDay currentDay : pattern.getDays())
    {
			// Falls zu wenig Touren oder ein Heimtag vorliegt, wird der Tag �bersprungen
      if (currentDay.isHomeDay()|| currentDay.getAmountOfTours()<tournrdestages)
      {
      	continue;
      }
    	
      // Bestimme x-te Tour des Tages
      HTour currentTour = currentDay.getTour(currentDay.getLowestTourIndex()+(tournrdestages-1));
    	
    	// F�hre Schritt nur f�r Touren aus, die noch keine festgelegte Startzeit haben
    	if (!currentTour.isScheduled())
      {
    		// Ermittle Entscheidung aus Schritt DC-Modellschritt  		
        double chosenStartCategory = (double) currentTour.getAttributesMap().get("tourStartCat_index");
        
        // Vorbereitungen und Objekte erzeugen
        String stepID = id + (int) chosenStartCategory;
        DefaultMCModelStep step = new DefaultMCModelStep(stepID, this);
        char mainActivityTypeInTour = currentTour.getActivity(0).getType();
        step.setModifiedDTDtoUse(mainActivityTypeInTour, (int) chosenStartCategory);
        // angepasste Zeitverteilungen aus vorherigen Entscheidungen werden nur im koordinierten Fall verwendet
	      step.setModifyDTDAfterStep(Configuration.coordinated_modelling);
        //step.setOutPropertyName("tourStartTime");
        step.setDTDTypeToUse(INDICATOR_TOUR_STARTTIMES);
        int[] bounds = calculateStartingBoundsForTours(currentDay, currentTour, false);
        step.setRangeBounds(bounds[0], bounds[1]);
        
        // Entscheidung durchf�hren
        step.doStep();
        
        // Speichere Ergebnisse ab
        int chosenStartTime = step.getChosenTime();
        currentTour.setStartTime(chosenStartTime);   
      }
    }
  }	
  
  	 
    
	/**
	 * 
	 * @throws InvalidPersonPatternException
	 */
	private void executeStep10ST() throws InvalidPersonPatternException
	{
    // Step 10s and Step10t: determine home time before tour starts and then define tour start time
		//											 only for the fourth tour if the day and following

    // reset tour start dtds
    modifiedTourStartDTDs = new DiscreteTimeDistribution[Configuration.NUMBER_OF_ACTIVITY_TYPES][Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES];
    for (HDay currentDay : pattern.getDays())
    {
    	if (currentDay.isHomeDay() || currentDay.getAmountOfTours()< 4)
      {
      	continue;
      }
      for (int j=currentDay.getLowestTourIndex()+3; j<=currentDay.getHighestTourIndex(); j++)
      {
      	HTour currentTour = currentDay.getTour(j);
      	// Bestimme Heimzeit vor Tour
        if (!currentTour.isScheduled())
        {
        	// 10S
        	
	        	// AttributeLookup erzeugen
	      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour);   	
	        	
	    	    // Step-Objekt erzeugen
	    	    DefaultDCModelStep dcstep = new DefaultDCModelStep("10S", this, lookup);
	    	     		
	          // Bestimme Ober- und Untergrenze und schr�nke Alternativenmenge ein
	          int dcbounds[] = calculateBoundsForHomeTime(currentTour, true);
	    	    int lowerbound = dcbounds[0];
	    	    int upperbound = dcbounds[1];
	    	    dcstep.limitAlternatives(lowerbound, upperbound);
	    	    
	    	    // F�hre Entscheidungswahl durch
	    	    dcstep.doStep();
	
	    	    // Eigenschaft abspeichern
	    	    int chosenHomeTimeCategory = dcstep.getDecision();
        	
	    	    // 10T
	    	    
	    	    // Vorbereitungen und Objekte erzeugen
	          String stepID = "10T" + (int) chosenHomeTimeCategory;
	          DefaultMCModelStep mcstep = new DefaultMCModelStep(stepID, this);
	          char mainActivityTypeInTour = currentTour.getActivity(0).getType();
	          mcstep.setModifiedDTDtoUse(mainActivityTypeInTour, (int) chosenHomeTimeCategory);
	          // angepasste Zeitverteilungen aus vorherigen Entscheidungen werden nur im koordinierten Fall verwendet
	  	      mcstep.setModifyDTDAfterStep(Configuration.coordinated_modelling);
	          //step.setOutPropertyName("tourStartTime");
	          mcstep.setDTDTypeToUse(INDICATOR_ACT_DURATIONS);
	          int[] mcbounds = calculateBoundsForHomeTime(currentTour, false);
	          mcstep.setRangeBounds(mcbounds[0], mcbounds[1]);
	          
	          // Entscheidung durchf�hren
	          mcstep.doStep();
	          
	          // Speichere Ergebnisse ab
	          int starttimetour = currentDay.getTour(currentTour.getIndex()-1).getEndTime() + mcstep.getChosenTime();
	          currentTour.setStartTime(starttimetour);
	          
	          // Setze die Startzeiten der Aktivit�ten in dieser Tour
	          createStartTimesforActivities(currentTour);
        }
      }
    }
	}    
    
	/**
	 * 
	 * @param id
	 */
	private void executeStep11(String id)
	{
		
    // STEP 11 - Decision on joint activities
    for (HDay currentDay : pattern.getDays())
    {
    	// Ist der Tag durch Home bestimmt, wird der Schritt nicht ausgef�hrt
    	if (currentDay.isHomeDay())
      {
      	continue;
      }
      
      for (HTour currentTour : currentDay.getTours())
      {
        for (HActivity currentActivity : currentTour.getActivities())
        {
        	/* 
        	 * Falls die Aktivit�t nicht von der Person selbst erzeugt wurde sondern von einer anderen Person stammt
        	 * und als gemeinsame Aktivit�t �bernommen wurde, wird der Schritt �bersprungen
        	 */
        	if (currentActivity.getCreatorPersonIndex() != person.getPersIndex())
        	{
        		continue;
        	}
        	
        	
        	/*
      		 * Schritte nur durchf�hren, falls Person nicht als letzte Person eines Haushalts modelliert wird
      		 * Bei letzter Person im Haushalt k�nnen keine weiteren neuen gemeinsamen Aktivit�ten mehr erzeugt werden!
      		 */
      		if ((int) person.getAttributefromMap("numbermodeledinhh") != person.getHousehold().getNumberofPersonsinHousehold())
      		{
	        	// AttributeLookup erzeugen
	      		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, currentActivity);   	
	        	
	    	    // Step-Objekt erzeugen
	    	    DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
	    	    step.doStep();
	
	          // Status festlegen
	    	    currentActivity.setJointStatus(Integer.parseInt(step.getAlternativeChosen()));
      		}
    	    else
    	    {
    	    	// Falls letzte Person, sind keine weiteren gemeinsamen Aktionen m�glich
	    	    currentActivity.setJointStatus(4);
    	    }
        }
      }
    }
	}

    
    
	/**
	 * 
	 * Bestimmung des genaueren Aktivit�tenzwecks f�r den Zweck Shopping 'S', 'L' und 'W'
	 * 
	 * @param activity
	 * @param id
	 */
	private void executeStep98(HActivity activity, String id)
	{
	  // STEP 98A-C Verfeinerung Aktivit�tenzweck SHOPPING, LEISURE und WORK
	  
		HDay currentDay = activity.getDay();
		HTour currentTour = activity.getTour();
	
		// AttributeLookup erzeugen
		AttributeLookup lookup = new AttributeLookup(person, currentDay, currentTour, activity);   	
		
	  // Step-Objekt erzeugen
	  DefaultDCModelStep step = new DefaultDCModelStep(id, this, lookup);
	  step.doStep();
	
	  // Speichere gew�hlte Entscheidung f�r weitere Verwendung
	  int chosenActivityType = Integer.parseInt(step.getAlternativeChosen());
	  
	  // Aktivit�tstyp festlegen
	  activity.setMobiToppActType((byte) chosenActivityType);          
	}



	/**
	 * 
	 * limits the range of alternatives if "standarddauer == 1" in step 8a. See documentation for further details
	 * 
	 * @param activity
	 * @param step
	 */
	private void modifyAlternativesDueTo8A(HActivity activity, DefaultDCModelStep step)
	{
    // Limitiere die Alternativen, falls Ergebnis von 8A YES ist
    if (activity.getAttributesMap().get("standarddauer") == 1.0d)
    {
    	// Ermittle die Standard-Zeitkategorie f�r den Tag und den Zweck
      int timeCategory = activity.calculateMeanTimeCategory();
      	        
      int from = timeCategory - 1;
      int to = timeCategory + 1;
        
      // Behandlung der Sonderf�lle
      
      // untere Grenze liegt in Zeitklasse 0
      if (from<0)
      {
      	from=0;
      }
      // obere Grenze liegt in letzter Zeitklasse
      if (to>Configuration.NUMBER_OF_ACT_DURATION_CLASSES-1)
      {
      	to=Configuration.NUMBER_OF_ACT_DURATION_CLASSES-1;
      }
        
      step.limitAlternatives(from, to);
      // add utility bonus of 10% to average time class (middle of the 3 selected)
      step.applyUtilityModification(timeCategory, 1.10);
    }
	}


	/**
	 * 
	 * Bestimmt die Obergrenze f�r die Aktivit�tendauern auf Basis bereits geplanter Aktivit�ten.
	 * 
	 * @param act
	 * @return
	 * @throws InvalidPersonPatternException
	 */
	private int calculateMaxdurationDueToScheduledActivities(HActivity act) throws InvalidPersonPatternException, InvalidHouseholdPatternException
	{
		HDay dayofact = act.getDay();
		
		/*
		 * Grundidee der Bestimmung der unteren Grenze
		 * 
		 * 1. Ausgangspunkt (in absteigender Priorit�t)
		 * - Es gibt bereits eine vorhergehende Aktivit�t mit festgelegter Startzeit am Tag
		 * - Die letzte Aktivit�t des Vortags ragt in den aktuellen Tag hinein
		 * - Anfang des Tages (1 Minute Puffer f�r Heimzeiten)
		 * 
		 * 2. Ermittel alle Aktivit�tendauern zwischen Tagesanfang / letzter Aktivit�t und der aktuellen Akt
		 * 3. Ermittel alle Wegdauern zwischen Tagesanfang / letzter Aktivit�t und der aktuellen Akt
		 * 4. Ermittel Puffer f�r Heimzeiten f�r alle Touren zwischen Tagesanfang / Tour der letzten Aktivit�t und der aktuellen Akt
		 * 
		 * 
		 * Grundidee der Bestimmung der oberen Grenze
		 * 
		 * 1. Ausgangspunkt (in absteigender Priorit�t)
		 * - Es gibt bereits eine nachfolgende Aktivit�t mit festgelegter Startzeit am Tag
		 * - Ende des Tages
		 * 
		 * Schritte 2-4 analog
		 */
		
		
		/*
		 * 1.
		 * 
		 * Ermittel die Ausgangspunkte
		 * 
		 */
		HActivity last_act_scheduled = null;
		HActivity next_act_scheduled = null;

		for (HActivity tmpact : dayofact.getAllActivitiesoftheDay())
		{
			
			// Suche nach letzter im Tagesverlauf bereits festgelegter Startzeit einer Aktivit�t
			if(act.compareTo(tmpact)==-1)		// Findet alle fr�heren Aktivit�t als die Aktivit�t selbst	
			{
				//System.out.println(tmpact.getTour().getIndex() + "/" + tmpact.getIndex());
				if(tmpact.startTimeisScheduled() && (last_act_scheduled==null || tmpact.getStartTime()>last_act_scheduled.getStartTime())) last_act_scheduled = tmpact;
			}	
			
			// Suche nach n�chster im Tagesverlauf bereits festgelegter Startzeit einer Aktivit�t
			if(act.compareTo(tmpact)==+1)
			{
				//System.out.println(tmpact.getTour().getIndex() + "/" + tmpact.getIndex());
				if(tmpact.startTimeisScheduled() && (next_act_scheduled==null || tmpact.getStartTime()<next_act_scheduled.getStartTime())) next_act_scheduled = tmpact;
			}	
		}
		
		/*
		 * Bestimme Ausgangspunkt der unteren Grenze
		 */
		
		int ausgangspunktunteregrenze=1;
		if (last_act_scheduled!=null)
		{
			ausgangspunktunteregrenze = last_act_scheduled.getStartTime() + (last_act_scheduled.durationisScheduled() ?  last_act_scheduled.getDuration() : Configuration.FIXED_ACTIVITY_TIME_ESTIMATOR); 
		}
		else
		{
			// Pr�fe, ob letzte Akt des Vortages in den aktuellen Tag ragt!
			HDay vortag = dayofact.getPreviousDay();
			if (vortag!=null && !vortag.isHomeDay())
			{
				HActivity letzteaktvortag = vortag.getLastTourOfDay().getLastActivityInTour();
				if (letzteaktvortag.startTimeisScheduled())
				{
					int endeletzteaktvortag = letzteaktvortag.getStartTime() +
							(letzteaktvortag.durationisScheduled() ? letzteaktvortag.getDuration() : 0) + 
							(letzteaktvortag.tripAfterActivityisScheduled() ? letzteaktvortag.getEstimatedTripTimeAfterActivity() : 0);
					if (endeletzteaktvortag>1440) 
					{
						ausgangspunktunteregrenze = endeletzteaktvortag-1440;
					}
				}
			}
		}
	
		/*
		 * Bestimme Ausgangspunkt der oberen Grenze
		 */	

		int ausgangspunktoberegrenze=0;
		if (next_act_scheduled!=null)
		{
			ausgangspunktoberegrenze = next_act_scheduled.getStartTime(); 
		}
		else
		{
			ausgangspunktoberegrenze = 1440;
		}
	
		
		/*
		 * 2.
		 * 
		 * Ermittel die Aktivit�tendauern
		 * 
		 */
		
		int activitydurationsincelastscheduled = countActivityDurationsbetweenActivitiesofOneDay(last_act_scheduled, act);
		int activitydurationuntilnextscheduled = countActivityDurationsbetweenActivitiesofOneDay(act, next_act_scheduled);
		
		/*
		 * 3.
		 * 
		 * Ermittel die Wegdauern
		 * 
		 */
		
		int tripdurationssincelastscheduled = countTripDurationsbetweenActivitiesofOneDay(last_act_scheduled, act);
		int tripdurationsuntilnextscheduled = countTripDurationsbetweenActivitiesofOneDay(act, next_act_scheduled);
	
		/*
		 * 4.
		 * 
		 * Ermittel die Heimzeitpuffer
		 * Ber�cksichtige jeweils 1 Minute pro Heimakt als Mindestpuffer
		 * 
		 */

		/*
		 * Vorher
		 */
		int timeforhomeactsincelastscheduled=0;		
	  if (last_act_scheduled==null)
		{
			// Z�hle wieviele Touren vor der aktuelle Tour liegen
	  	timeforhomeactsincelastscheduled += (act.getTour().getIndex() - act.getDay().getLowestTourIndex());
		}
		else
		{
			// Z�hle wieviele Touren zwischen der der letzten festgelegten und der aktuellen liegen
			timeforhomeactsincelastscheduled += (act.getTour().getIndex() - last_act_scheduled.getTour().getIndex());
		}
	  
	  /*
	   * Nachher
	   */
		int timeforhomeactuntilnextscheduled=0;
	  if (next_act_scheduled==null)
		{
			// Z�hle wieviele Touren nach der aktuellen Tour noch kommen
	  	timeforhomeactuntilnextscheduled += (act.getDay().getHighestTourIndex() - act.getTour().getIndex());
		}
		else
		{
			// Z�hle wieviele Touren zwischen der der n�chsten festgelegten und der aktuellen liegen
			timeforhomeactuntilnextscheduled += (next_act_scheduled.getTour().getIndex() - act.getTour().getIndex());
		}
		
		/*
		 * 5.
		 * 
		 * Bestimme Schranken und maximale Dauern
		 * 
		 */
	  
	  //DEBUG ONLY
	  if (person.getPersIndex()==517 && act.getIndexDay()==6 && act.getTour().getIndex()==0 && act.getIndex()==3)
	  {
	  	System.out.println("H");
	  }
		
		
		// Bestimme obere und untere Schranken
		int lowerbound = ausgangspunktunteregrenze + activitydurationsincelastscheduled + tripdurationssincelastscheduled + timeforhomeactsincelastscheduled;
		int upperbound = ausgangspunktoberegrenze - activitydurationuntilnextscheduled - tripdurationsuntilnextscheduled + timeforhomeactuntilnextscheduled;
		
		int maxduration = upperbound - lowerbound;
    // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
    if (upperbound<lowerbound)
    {
    	if (next_act_scheduled!= null && next_act_scheduled.getCreatorPersonIndex()!=person.getPersIndex() && 
    			last_act_scheduled!=null && last_act_scheduled.getCreatorPersonIndex()!=person.getPersIndex())
    	{
    		String errorMsg = "Duration Bounds incompatible Act (Household Exception) " + act.getIndexDay() + "/" + act.getTour().getIndex() + "/" + act.getIndex() + " : UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
    		throw new InvalidHouseholdPatternException(pattern, errorMsg);
    	}
    	else
    	{
    		String errorMsg = "Duration Bounds incompatible Act (Person Exception) " + act.getIndexDay() + "/" + act.getTour().getIndex() + "/" + act.getIndex() + " : UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
    		throw new InvalidPersonPatternException(pattern, errorMsg);
    	}
    }
		
		return maxduration;
	}
	
	private int getDurationTimeClassforExactDuration (int maxduration)
	{
    int timeClass=-1;
		
		// Bestimme die daraus resultierende Zeitklasse
    for (int i = 0; i < Configuration.NUMBER_OF_ACT_DURATION_CLASSES; i++)
    {
        if (maxduration >= Configuration.ACT_TIME_TIMECLASSES_LB[i] && maxduration <= Configuration.ACT_TIME_TIMECLASSES_UB[i])
        {
        	timeClass = i;
        }
    }  
    assert timeClass!=-1 : "TimeClass konnte nicht bestimmt werden!";
    return timeClass;
	}
	
	/**
	 * 
	 * Bestimmt die Aktivit�tendauern zwischen zwei Aktivit�ten eines Tages
	 * 
	 * @param actfrom
	 * @param actto
	 * @return
	 */
	private int countActivityDurationsbetweenActivitiesofOneDay(HActivity actfrom, HActivity actto) 
	{
		int result=0;
		List<HActivity> tagesaktliste;
		if (actfrom==null)
		{
			tagesaktliste = actto.getDay().getAllActivitiesoftheDay();
		}
		else 
		{
			tagesaktliste = actfrom.getDay().getAllActivitiesoftheDay();
		}
		
		for (HActivity tmpact : tagesaktliste)
		{
			// Suche alle Aktivit�ten die zwischen from und to liegen und addiere die Aktivit�tszeit auf das Ergebnis
			if (	 (actfrom== null && actto!= null 																&& actto.compareTo(tmpact)<0)
					|| (actfrom!= null && actto!= null && actfrom.compareTo(tmpact)>0	&& actto.compareTo(tmpact)<0)
					|| (actfrom!= null && actto== null && actfrom.compareTo(tmpact)>0															)
					)
			{
				if (tmpact.durationisScheduled())
				{
					result += tmpact.getDuration();
				}
				else
				{
					result += Configuration.FIXED_ACTIVITY_TIME_ESTIMATOR;
				}
			}
		}
		return result;
	}
	
	/**
	 * 
	 * Bestimmt die Wegdauern zwischen zwei Aktivit�ten eines Tages
	 * 
	 * @param actfrom
	 * @param actto
	 * @return
	 */
	private int countTripDurationsbetweenActivitiesofOneDay(HActivity actfrom, HActivity actto) 
	{
		int result=0;		
		List<HActivity> tagesaktliste;
		if (actfrom==null)
		{
			tagesaktliste = actto.getDay().getAllActivitiesoftheDay();
		}
		else 
		{
			tagesaktliste = actfrom.getDay().getAllActivitiesoftheDay();
		}
		
		for (HActivity tmpact : tagesaktliste)
		{
			// Suche alle Aktivit�ten die zwischen from und to (inkl. to) liegen und addiere die Wegzeiten auf das Ergebnis
			if (	 (actfrom== null && actto!= null 																  && actto.compareTo(tmpact)<=0)
					|| (actfrom!= null && actto!= null && actfrom.compareTo(tmpact)>=0	&& actto.compareTo(tmpact)<=0)
					|| (actfrom!= null && actto== null && actfrom.compareTo(tmpact)>=0															 )
				 )
			{
				if (actto != null && actto.compareTo(tmpact)==0)
				{
					result += tmpact.getEstimatedTripTimeBeforeActivity();
				}
				else if (actfrom != null && actfrom.compareTo(tmpact)==0)
				{
					if (tmpact.isActivityLastinTour()) result += tmpact.getEstimatedTripTimeAfterActivity();
				}
				else
				{
					result += tmpact.getEstimatedTripTimeBeforeActivity();
					if (tmpact.isActivityLastinTour()) result += tmpact.getEstimatedTripTimeAfterActivity();
				}
			}
		}
		return result;
	}
	

	/**
	 * 
	 * @param day
	 * @param tour
	 * @param categories
	 * @return
	 * @throws InvalidPersonPatternException
	 */
	private int[] calculateBoundsForHomeTime(HTour tour, boolean categories) throws InvalidPersonPatternException
  {
		HDay tourday = tour.getDay();
		
  	// lowerbound startet mit 1 - upperbound mit 1440 (maximale Heimzeit)
    int lowerbound = 1;
    int upperbound = 1440;
    
    int lowercat = -1;
    int uppercat = -1;   
    
    // Bestimme obere Grenze basierend auf bereits festgelegten Startzeitpunkten der im weiteren Tagesverlauf folgenden Touren
 	  int tmptourdurations = 0;
 	  for (int i = tour.getIndex(); i <= tourday.getHighestTourIndex(); i++)
 	  {
 	  	HTour tmptour = tourday.getTour(i);
 	  	
 	  	// Sobald eine bereits geplante Tour gefunden wurde wird von diesem Punkt ausgegangen die obere Grenze berechnet
 	  	if (tmptour.isScheduled())
 	  	{
 	  		upperbound = tmptour.getStartTime() - tmptourdurations;
 	  		break;
 	  	}
 	  	// Sollte die Tour noch nicht verplant sein wird die Dauer der Tour in die Grenzenberechnung mit einbezogen
 	  	else
 	  	{
 	  		// +1 um jeweils nach der Tour noch eine Heimaktivit�t von min. einer Minute zu erm�glichen
 	  		tmptourdurations += tmptour.getTourDuration() + 1;
 	  	}
 	  	// Falls Schleife bis zur letzten Tour l�uft gibt es keine festgelegten Startzeiten und die Obergrenze kann basierend auf den Tourdauern bestimmt werden
 	  	if (tmptour.getIndex()==tourday.getHighestTourIndex())
 	  	{
 	  		upperbound -= tmptourdurations;
 	  	}
 	  }
 	  
 	  // Upperbound wird zus�tzlich durch das Ende der vorherigen Tour (= schon verbrauchte Zeit) bestimmt
    upperbound -= tourday.getTour(tour.getIndex()-1).getEndTime();
       
    
          
    // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
    if (upperbound<lowerbound)
    {
    	String errorMsg = "HomeTime Tour " + tour.getIndex() + " : UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
    	throw new InvalidPersonPatternException(pattern, errorMsg);
    }

    // Zeitklassen falls erforderlich
    if(categories)
    {
      // Setze die Zeiten in Kategorien um
      for (int i=0; i<Configuration.NUMBER_OF_HOME_DURATION_CLASSES; i++)
      {
      	if (lowerbound>=Configuration.HOME_TIME_TIMECLASSES_LB[i] && lowerbound<=Configuration.HOME_TIME_TIMECLASSES_UB[i])
      	{
      		lowercat =i;
      	}
      	if (upperbound>=Configuration.HOME_TIME_TIMECLASSES_LB[i] && upperbound<=Configuration.HOME_TIME_TIMECLASSES_UB[i])
      	{
      		uppercat =i;
      	}
      }
    }
            
    // Fehlerbehandlung, falls Kategorien nicht gesetzt werden konnten
    if(categories)
    {
	    if (uppercat==-1 || lowercat==-1)
	    {
	    	String errorMsg = "HomeTime Tour " + tour.getIndex() + " : Could not identify categories - UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	    	throw new InvalidPersonPatternException(pattern, errorMsg);
	    }
    }
      
    int[] bounds = new int[2];
    if (categories)
    {
    	bounds[0] = lowercat;
    	bounds[1] = uppercat;
    }
    if (!categories)
    {
    	bounds[0] = lowerbound;
    	bounds[1] = upperbound;
    }
    return bounds;
  }
	
	
	
	/**
	 * 
	 * Bestimmt die Ober- und Untergrenze der Startzeiten f�r Touren basierend auf m�glichen schon festgelegten Startzeiten und Dauern
	 * Boolean-Wert categories bestimmt, ob die Zeitkategorien oder die konkreten Grenzwerte zur�ckgegeben werden
	 * 
	 * @param categories
	 * @param tour
	 * @return
	 * @throws InvalidPersonPatternException
	 */
	private int[] calculateStartingBoundsForTours(HTour tour, boolean categories) throws InvalidPersonPatternException
	{

		/*
		 * Grundidee der Bestimmung der unteren Grenze
		 * 
		 * 1. Ausgangspunkt (in absteigender Priorit�t)
		 * - Die Tour ist nicht die erste Tour des Tages -> Es gibt bereits die Endezeit der vorhergehenden Tour + 1
		 * - Die letzte Aktivit�t des Vortags ragt in den aktuellen Tag hinein
		 * - Anfang des Tages
		 * 
		 * 
		 * Grundidee der Bestimmung der oberen Grenze
		 * 
		 * 1. Ausgangspunkt (in absteigender Priorit�t)
		 * - Pr�fe, ob es bereits eine weitere geplante Anfangszeit einer Tour im Tagesverlauf gibt
		 * - Pr�fe, ob es am n�chsten Tag bis 3 Uhr morgens schon eine geplante Aktivit�t gibt
		 * - 3 Uhr Nachts des Folgetages als sp�testens Ende der Tour = 1620
		 * 
		 * 2. Alle noch geplanten Touren inkl. aller Aktivit�ts- und Wegzeiten abziehen zwischen Tagesende bzw. n�chster geplanter Tour
		 * 3. Puffer f�r Heimaktivit�ten zwischen den Touren
		 * 
		 */
		  
	  HDay tourday = tour.getDay();
	 
	  int lowercat = -1;
	  int uppercat = -1; 
	  
	  //TODO DEBUG ONLY
	  if (person.getPersIndex()==2 && tourday.getIndex()==1 && tour.getIndex()==-1)
	  {
	  	System.out.println("H");
	  }
		/*
		 * 
		 * untere Grenze
		 * 
		 */

	  int basisunteregrenze = 1;
	 
	  
	  // Falls es sich nicht um die erste Tour des Tages handelt, wird lowerbound durch das Ende der vorhergehenden Tour bestimmt
	  if (tour.getIndex() != tourday.getLowestTourIndex())
	  {
	  	basisunteregrenze = tourday.getTour(tour.getIndex()-1).getEndTime() + 1;
	  }
	  // Ansonsten pr�fe, ob letzte Aktivit�t des Vortags noch in den aktuellen Tag ragt
	  else
	  {
			// Pr�fe, ob letzte Akt des Vortages in den aktuellen Tag ragt!
			HDay vortag = tourday.getPreviousDay();
			if (vortag!=null && !vortag.isHomeDay())
			{
				HActivity letzteaktvortag = vortag.getLastTourOfDay().getLastActivityInTour();
				if (letzteaktvortag.startTimeisScheduled())
				{
					int endeletzteaktvortag = letzteaktvortag.getStartTime() +
							(letzteaktvortag.durationisScheduled() ? letzteaktvortag.getDuration() : 0) + 
							(letzteaktvortag.tripAfterActivityisScheduled() ? letzteaktvortag.getEstimatedTripTimeAfterActivity() : 0);
					if (endeletzteaktvortag>1440) 
					{
						basisunteregrenze = endeletzteaktvortag-1440;
					}
				}
			}
	  }
	  

		/*
		 * 
		 * obere Grenze
		 * 
		 */

	  /*
	   * 1. Ausgangspunkt
	   */
	  int basisoberegrenze = 1620;
	  HTour nexttourscheduled=null;
	  
	  //Pr�fe, ob es im Tagesverlauf noch weitere geplante Touren gibt
	  for (int i = tour.getIndex()+1; i <= tourday.getHighestTourIndex(); i++)
	  {
	  	HTour tmptour = tourday.getTour(i);
	  	// Sobald eine bereits geplante Tour gefunden wurde wird von diesem Punkt ausgegangen die obere Grenze berechnet
	  	if (tmptour.isScheduled())
	  	{
	  		nexttourscheduled=tmptour;
	  		basisoberegrenze = tmptour.getStartTime();
	  		break;
	  	}
	  }
	  // Pr�fe, ob am Folgetag bis 3 Uhr nachts bereits die erste Aktivit�t geplant ist, falls keine weitere geplante Tour an diesem Tag
	  if (nexttourscheduled==null)
	  {
	  	HDay folgetag = tourday.getNextDay();
	  	if (folgetag!=null && !folgetag.isHomeDay())
			{
				HActivity ersteaktfolgetag = folgetag.getFirstTourOfDay().getFirstActivityInTour();
				if (ersteaktfolgetag.startTimeisScheduled())
				{
					int startersteaktfolgetag = ersteaktfolgetag.getStartTime() -
							(ersteaktfolgetag.tripBeforeActivityisScheduled() ? ersteaktfolgetag.getEstimatedTripTimeBeforeActivity() : 0);
					if (startersteaktfolgetag<180) 
					{
						basisoberegrenze = 1439 + startersteaktfolgetag;
					}
				}
			}	  						
	  }
	  
	  
	  /*
	   * 2. Aktivit�ts- und Wegzeiten bis Tagesende / n�chster geplanter Tour
	   * 3. Heimzeitpuffer
	   */
	  int tmptourdurations = 0;
	  int heimzeitpuffer = 0;
	  int tourindexfuersuche;
	  // Bestimme, bis zu welcher Tour die Dauern gez�hlt werden
	  if(nexttourscheduled!=null)
	  {
	  	// Falls n�chste Tour bekannt ist, werden alle Touren bis dahin gez�hlt
	  	tourindexfuersuche = nexttourscheduled.getIndex()-1;
	  }
	  else
	  {
	  	// Falls n�chste Tour nicht bekannt ist, werden alle restlichen Touren des Tages gez�hlt
	  	tourindexfuersuche = tourday.getHighestTourIndex();
	  }
	  for (int i = tour.getIndex(); i <= tourindexfuersuche; i++)
	  {
	  	HTour tmptour = tourday.getTour(i);
	  	tmptourdurations += tmptour.getTourDuration();
	  	
	  	heimzeitpuffer += 1;
	  }
	  
	  
	  /*
	   * 
	   * Grenzen bestimmen und falls notwendig Kategorien bilden
	   * 
	   */
	  
	  int lowerbound = basisunteregrenze;
	  int upperbound = basisoberegrenze - tmptourdurations - heimzeitpuffer;
	  
	  // UpperBound falls notwendig auf 1439 k�rzen, da keine sp�teren Anfangszeiten  m�glich
	  if (upperbound>1439) upperbound=1439;
	  
	        
	  // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
	  if (upperbound<lowerbound)
	  {
	  	String errorMsg = "TourStartTimes Tour " + tourday.getIndex() + "/" + tour.getIndex() + " : UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	  	throw new InvalidPersonPatternException(pattern, errorMsg);
	  }
	
	  
	  // Zeitklassen f�r erste Tour des Tages
	  if(categories && tour.getIndex()== tourday.getLowestTourIndex())
	  {
	    // Setze die Zeiten in Kategorien um
	      for (int i=0; i<Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES; i++)
	      {
	      	if (lowerbound>=Configuration.MAIN_TOUR_START_TIMECLASSES_LB[i] && lowerbound<=Configuration.MAIN_TOUR_START_TIMECLASSES_UB[i])
	      	{
	      		lowercat =i;
	      	}
	      	if (upperbound>=Configuration.MAIN_TOUR_START_TIMECLASSES_LB[i] && upperbound<=Configuration.MAIN_TOUR_START_TIMECLASSES_UB[i])
	      	{
	      		uppercat =i;
	      	}
	      }
	    }
	
	  // Zeitklassen f�r zweite und dritte Tour des Tages
	  if(categories && tour.getIndex()!= tourday.getLowestTourIndex())
	  {
	    // Setze die Zeiten in Kategorien um
	    for (int i=0; i<Configuration.NUMBER_OF_SECTHR_START_TIME_CLASSES ; i++)
	    {
	    	if (lowerbound>=Configuration.SECTHR_TOUR_START_TIMECLASSES_LB[i] && lowerbound<=Configuration.SECTHR_TOUR_START_TIMECLASSES_UB[i])
	    	{
	    		lowercat =i;
	    	}
	    	if (upperbound>=Configuration.SECTHR_TOUR_START_TIMECLASSES_LB[i] && upperbound<=Configuration.SECTHR_TOUR_START_TIMECLASSES_UB[i])
	    	{
	    		uppercat =i;
	    	}
	    }
	  }
	          
	  // Fehlerbehandlung, falls Kategorien nicht gesetzt werden konnten
	  if(categories)
	  {
	    if (uppercat==-1 || lowercat==-1)
	    {
	    	String errorMsg = "TourStartTimes Tour " + tour.getIndex() + " : Could not identify categories - UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	    	throw new InvalidPersonPatternException(pattern, errorMsg);
	    }
	  }
	    
	  int[] bounds = new int[2];
	  if (categories)
	  {
	  	bounds[0] = lowercat;
	  	bounds[1] = uppercat;
	  }
	  if (!categories)
	  {
	  	bounds[0] = lowerbound;
	  	bounds[1] = upperbound;
	  }
	  return bounds;
	}



	/**
	 * 
	 * max time class to be chosen= min(1440-totalDailyTripTime-durationOfMainActs, mainActDur-1))
	 * 
	 * Vereinfacht durch einheitlichere Methode calculateUpperBoundDurationTimeClassDueToPlannedDurations
	 * 
	 * @param day
	 * @param acttour
	 * @param step8j
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private void modifyAlternativesForStep8J(HDay day, HTour acttour, DefaultDCModelStep step8j)
	{
	  //Optimierungspotential - die Aktivit�t darf nicht l�nger sein als die noch �brige Zeit - 
	  //nicht nur abh�ngig von den Hauptaktivit�ten sondern auch den restlichen bisher festgelegten Aktivit�ten
	  
		// Obergrenze 1 - bisher festgelegte "verbrauchte" Zeiten am Tag
		int totalMainActivityTime = 0;
		// Alle Hauptaktivit�ten + zugeh�rige Wege + letzter Weg am Ende der Tour
	    for (HTour tour : day.getTours())
	    {
	    	totalMainActivityTime += tour.getActivity(0).getDuration() + tour.getActivity(0).getEstimatedTripTimeBeforeActivity() + tour.getLastActivityInTour().getEstimatedTripTimeAfterActivity();
	    }
	    // Obergrenze 1
	    int remainingTimeUpperBound = 1440 - totalMainActivityTime;
	  
	  // Obergrenze 2 - Aktivit�t muss k�rzer sein als Hauptaktivit�t auf Tour 
	
	    // Tim (08.11.2016) - Obergrenze default auf 99999 Minuten gesetzt, das hei�t ohne Wirkung, da sonst zu kurze Aktivit�ten)
	    // int maxVNActTimeUpperBound = acttour.getActivity(0).getDuration() - 1;
	    int maxVNActTimeUpperBound = 9999;
	
	  int maxTimeForActivity = Math.min(remainingTimeUpperBound, maxVNActTimeUpperBound);
	
	  // Bestimme die daraus resultierende Zeitklasse
	  int maxTimeClass = 0;
	  for (int i = 1; i < Configuration.NUMBER_OF_ACT_DURATION_CLASSES; i++)
	  {
	      if (maxTimeForActivity >= Configuration.ACT_TIME_TIMECLASSES_LB[i] && maxTimeForActivity <= Configuration.ACT_TIME_TIMECLASSES_UB[i])
	      {
	          maxTimeClass = i;
	      }
	  }
	  step8j.limitAlternatives(0, maxTimeClass);
	
	}



	/**
	 * 
	 * limits the logit alternatives for step10b and calculates upper and lower bounds
	 * 
	 * @param day
	 * @param tour
	 * @param modelstep
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	private void modifyAlternativesForStep10B(HDay day, HTour tour, DefaultDCModelStep modelstep) throws InvalidPersonPatternException
	{
		// Bestimme die Grenzen auf Basis bereits festgelegter Aktivit�tszeiten
	  int bounds[] = calculateStartingBoundsForMainTours(day, true);
	  int lowerbound = bounds[0];
	  int upperbound = bounds[1];
	
	  // Pr�fe, ob die Tour im Standard-Startzeitraum liegt
	  boolean tourInStdStartTime = false;
	
	  double val = tour.getAttributesMap().get("default_start_cat_yes");
	  tourInStdStartTime = (val >= 1.0) ? true : false;
	
	  if (tourInStdStartTime)
	  {
	  	int default_start_category = (int) person.getAttributefromMap("main_tours_default_start_cat");
	  	// Standard-Startzeitraum liegt innerhalb der Grenzen
	  	if (default_start_category >= lowerbound && default_start_category<= upperbound)
	  	{
	  		lowerbound = default_start_category;
	  		upperbound = default_start_category;
	  	}
	  	// Standard-Startzeitraum liegt unterhalb der Untergrenze
	  	if (default_start_category < lowerbound)
	  	{
	  		// lowerbound = lowerbound;
	  		upperbound = lowerbound;
	  	}
	  	// Standard-Startzeitraum liegt oberhalb der Obergrenze
	  	if (default_start_category < lowerbound)
	  	{
	  		lowerbound = upperbound;
	  		// upperbound = upperbound;
	  	}
	  }
	  // Schr�nke die Alternativen entsprechend der Grenzen ein
	  modelstep.limitAlternatives(lowerbound, upperbound);
	
	}



	@Deprecated
	private int calculateUpperBoundDurationTimeClassDueToPlannedDurations(HDay day)
	{
		// verbleibende Zeit am Tag f�r Aktivit�ten
		int remainingTimeonDay = 1440 - (day.getTotalAmountOfActivityTime() + day.getTotalAmountOfTripTime());
		
		// Obergrenze 2 (f�r NICHT-Hauptaktivit�ten) - Aktivit�t muss k�rzer sein als Hauptaktivit�t auf Tour 
		// Tim (08.11.2016) - Obergrenzeinaktiv gesetzt, das hei�t ohne Wirkung, da sonst zu kurze Aktivit�ten
		
	  // Bestimme die daraus resultierende Zeitklasse
	  int maxTimeClass = 0;
	  for (int i = 1; i < Configuration.NUMBER_OF_ACT_DURATION_CLASSES; i++)
	  {
	      if (remainingTimeonDay >= Configuration.ACT_TIME_TIMECLASSES_LB[i] && remainingTimeonDay <= Configuration.ACT_TIME_TIMECLASSES_UB[i])
	      {
	          maxTimeClass = i;
	      }
	  }  
	  return maxTimeClass;
	}



	/**
   * 
   * Bestimmt die Ober- und Untergrenze der Startzeiten f�r Touren
   * Boolean-Wert categories bestimmt, ob die Zeitkategorien oder die konkreten Grenzwerte zur�ckgegeben werden
   * 
   * @param categories
   * @param day
   * @param tour
   * @return
   * @throws InvalidPersonPatternException
   */
	@Deprecated
  private int[] calculateStartingBoundsForTours(HDay day, HTour tour, boolean categories) throws InvalidPersonPatternException
  {
  	// lowerbound startet mit 1 - upperbound mit 1440 (0 Uhr n�chster Tag)
    int lowerbound = 1;
    int upperbound = 1440;
    
    int lowercat = -1;
    int uppercat = -1;
         
    // Falls es sich nicht um die erste Tour des Tages handelt, wird lowerbound durch das Ende der vorhergehenden Tour bestimmt
    if (tour.getIndex() != day.getLowestTourIndex())
    {
    	lowerbound = day.getTour(tour.getIndex()-1).getEndTime() + 1;
    }
    
    // Gehe alle Touren des Tages durch und ber�cksichtige bereits feststehende Dauern f�r die Festlegung der Grenzen
    // +1 um jeweils nach der Tour noch eine Heimaktivit�t von min. einer Minute zu erm�glichen
    for (int i = tour.getIndex(); i <= day.getHighestTourIndex(); i++)
    {
    	upperbound -= day.getTour(i).getTourDuration() + 1;
    }
          
    // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
    if (upperbound<lowerbound)
    {
    	String errorMsg = "TourStartTimes Tour " + tour.getIndex() + " : UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
    	throw new InvalidPersonPatternException(pattern, errorMsg);
    }

    // Zeitklassen f�r erste Tour des Tages
    if(categories && tour.getIndex()== day.getLowestTourIndex())
    {
      // Setze die Zeiten in Kategorien um
        for (int i=0; i<Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES; i++)
        {
        	if (lowerbound>=Configuration.MAIN_TOUR_START_TIMECLASSES_LB[i] && lowerbound<=Configuration.MAIN_TOUR_START_TIMECLASSES_UB[i])
        	{
        		lowercat =i;
        	}
        	if (upperbound>=Configuration.MAIN_TOUR_START_TIMECLASSES_LB[i] && upperbound<=Configuration.MAIN_TOUR_START_TIMECLASSES_UB[i])
        	{
        		uppercat =i;
        	}
        }
	    }
 
	    // Zeitklassen f�r zweite und dritte Tour des Tages
    if(categories && tour.getIndex()!= day.getLowestTourIndex())
    {
      // Setze die Zeiten in Kategorien um
      for (int i=0; i<Configuration.NUMBER_OF_SECTHR_START_TIME_CLASSES ; i++)
      {
      	if (lowerbound>=Configuration.SECTHR_TOUR_START_TIMECLASSES_LB[i] && lowerbound<=Configuration.SECTHR_TOUR_START_TIMECLASSES_UB[i])
      	{
      		lowercat =i;
      	}
      	if (upperbound>=Configuration.SECTHR_TOUR_START_TIMECLASSES_LB[i] && upperbound<=Configuration.SECTHR_TOUR_START_TIMECLASSES_UB[i])
      	{
      		uppercat =i;
      	}
      }
    }
            
    // Fehlerbehandlung, falls Kategorien nicht gesetzt werden konnten
    if(categories)
    {
	    if (uppercat==-1 || lowercat==-1)
	    {
	    	String errorMsg = "TourStartTimes Tour " + tour.getIndex() + " : Could not identify categories - UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	    	throw new InvalidPersonPatternException(pattern, errorMsg);
	    }
    }
      
    int[] bounds = new int[2];
    if (categories)
    {
    	bounds[0] = lowercat;
    	bounds[1] = uppercat;
    }
    if (!categories)
    {
    	bounds[0] = lowerbound;
    	bounds[1] = upperbound;
    }
    return bounds;
  }
  
  
  /**
   * 
   * Bestimmt die Ober- und Untergrenze der Startzeiten f�r Haupttouren
   * Boolean-Wert categories bestimmt, ob die Zeitkategorien oder die konkreten Grenzwerte zur�ckgegeben werden
   * 
   * @param categories
   * @param day
   * @return
   * @throws InvalidPersonPatternException
   */
  @Deprecated
  private int[] calculateStartingBoundsForMainTours(HDay day, boolean categories) throws InvalidPersonPatternException
  {
  	// lowerbound startet mit 0 - upperbound mit 1619 (2h59 nachts n�chster Tag)
    int lowerbound = 0;
    int upperbound = 1619;
    
    int lowercat = -1;
    int uppercat = -1;
    
    // Gehe alle Touren des Tages durch und ber�cksichtige bereits feststehende Dauern f�r die Festlegung der Grenzen
    for (HTour tour : day.getTours())
    {
    	// Touren vor der Haupttour
        if (tour.getIndex() < 0)
        {
        	lowerbound += tour.getTourDuration();
        }
        // Haupttour und Touren nach der Haupptour
        if (tour.getIndex() >= 0)
        {
            upperbound -= tour.getTourDuration();
        }
    }
    
    // Auf die untere Grenze kommt noch die Zeit f�r den Startweg der aktuellen Tour dazu
    lowerbound += Configuration.FIXED_TRIP_TIME_ESTIMATOR;
    
    
    // Die obere Grenze wird auf maximal 23.59 Uhr nachts gesetzt f�r den Startzeitpunkt der Tour
    upperbound = Math.min(upperbound, 1439);
    
    // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
    if (upperbound<lowerbound)
    {
    	String errorMsg = "MainTours: UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
    	throw new InvalidPersonPatternException(pattern, errorMsg);
    }

    if(categories)
    {
      // Setze die Zeiten in Kategorien um
      for (int i=0; i<Configuration.NUMBER_OF_MAIN_START_TIME_CLASSES; i++)
      {
      	if (lowerbound>=Configuration.MAIN_TOUR_START_TIMECLASSES_LB[i] && lowerbound<=Configuration.MAIN_TOUR_START_TIMECLASSES_UB[i])
      	{
      		lowercat =i;
      	}
      	if (upperbound>=Configuration.MAIN_TOUR_START_TIMECLASSES_LB[i] && upperbound<=Configuration.MAIN_TOUR_START_TIMECLASSES_UB[i])
      	{
      		uppercat =i;
      	}
      }
      
      // Fehlerbehandlung, falls Kategorien nicht gesetzt werden konnten
	    if (uppercat==-1 || lowercat==-1)
	    {
	    	String errorMsg = "MainTours: Could not identify categories - UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	    	throw new InvalidPersonPatternException(pattern, errorMsg);
	    }
    }
    
    int[] bounds = new int[2];
    if (categories)
    {
    	bounds[0] = lowercat;
    	bounds[1] = uppercat;
    }
    if (!categories)
    {
    	bounds[0] = lowerbound;
    	bounds[1] = upperbound;
    }
    return bounds;
  }


	/**
	 * 
	 * Bestimmt die Ober- und Untergrenze der Startzeiten f�r Touren vor der Haupttour
	 * Boolean-Wert categories bestimmt, ob die Zeitkategorien oder die konkreten Grenzwerte zur�ckgegeben werden
	 * 
	 * @param day
	 * @param tour
	 * @param categories
	 * @return
	 * @throws InvalidPersonPatternException
	 */
  @Deprecated
	private int[] calculateStartingBoundsForPreTours(HDay day, HTour tour, boolean categories) throws InvalidPersonPatternException
	{  
		HTour previousTour = tour.getPreviousTourinPattern();
    int lowerbound = 0;
    int upperbound = 0;
    int preTourTime = 0;
    
    int lowercat = -1;
    int uppercat = -1;

    if (previousTour != null)
    {
    	// Berechne den Endzeitpunkt der vorherigen Tour
    	preTourTime = previousTour.getStartTime() + previousTour.getTourDuration();

    	// Sonderbehandlung, wenn die Tour am Vortag stattgefunden hat
    	if(previousTour.getDay().getIndex()<tour.getDay().getIndex())
    	{
    		if (preTourTime > 1440)
	        {
	            preTourTime = preTourTime % 1440;
	        }
    		else
    		{
    			preTourTime = 0;
    		}
    	}        
    }
    preTourTime += Configuration.FIXED_TRIP_TIME_ESTIMATOR; // trip time to the first activity in THIS tour

    lowerbound = preTourTime;

    // UpperBound berechnen
    int preMainTourActivityDurationSum = day.getTotalAmountOfTourTimeUntilMainTour(tour);
    upperbound = day.getTour(0).getStartTime() - preMainTourActivityDurationSum;

      // Die obere Grenze wird auf maximal 23.59 Uhr nachts gesetzt f�r den Startzeitpunkt der Tour
      upperbound = Math.min(upperbound, 1439);
    
    // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
    if (upperbound<lowerbound)
    {
    	String errorMsg = "PreTours: UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
    	throw new InvalidPersonPatternException(pattern, errorMsg);
    }
    
    // Setze die Zeiten in Kategorien um
    if (categories)
    {
    	for (int i=0; i<Configuration.NUMBER_OF_PRE_START_TIME_CLASSES; i++)
	    {
	    	if (lowerbound>=Configuration.PRE_TOUR_START_TIMECLASSES_LB[i] && lowerbound<=Configuration.PRE_TOUR_START_TIMECLASSES_UB[i])
	    	{
	    		lowercat =i;
	    	}
	    	if (upperbound>=Configuration.PRE_TOUR_START_TIMECLASSES_LB[i] && upperbound<=Configuration.PRE_TOUR_START_TIMECLASSES_UB[i])
	    	{
	    		uppercat =i;
	    	}
	    }
	    
	    // Fehlerbehandlung, falls Kategorien nicht gesetzt werden konnten
	    if (uppercat==-1 || lowercat==-1)
	    {
	    	String errorMsg = "PreTours: Could not identify categories - UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	    	throw new InvalidPersonPatternException(pattern, errorMsg);
	    }
    }
	    
    int[] bounds = new int[2];
    if (categories)
    {
    	bounds[0] = lowercat;
    	bounds[1] = uppercat;
    }
    if (!categories)
    {
    	bounds[0] = lowerbound;
    	bounds[1] = upperbound;
    }
    return bounds;
	}


	/**
	 * 
	 * Bestimmt die Ober- und Untergrenze der Startzeiten f�r Touren nach der Haupttour
	 * Boolean-Wert categories bestimmt, ob die Zeitkategorien oder die konkreten Grenzwerte zur�ckgegeben werden
	 * 
	 * @param day
	 * @param tour
	 * @param categories
	 * @return
	 * @throws InvalidPersonPatternException
	 */
  @Deprecated
	private int[] calculateStartingBoundsForPostTours(HDay day, HTour tour, boolean categories) throws InvalidPersonPatternException
	{		
		HTour previousTour = tour.getPreviousTourinPattern();
    int lowerbound = 0;
    int upperbound = 0;
    int preTourTime = 0;

    int lowercat = -1;
    int uppercat = -1;
	    
		// Berechne den Endzeitpunkt der vorherigen Tour - LowerBound
		preTourTime = previousTour.getStartTime() + previousTour.getTourDuration();
		lowerbound = preTourTime;
		
	  // UpperBound berechnen
    int totalRemainingActivityTime = day.getTotalAmountOfRemainingTourTime(tour);
    upperbound = 1619 - totalRemainingActivityTime;
    
    // Die obere Grenze wird auf maximal 23.59 Uhr nachts gesetzt f�r den Startzeitpunkt der Tour
    upperbound = Math.min(upperbound, 1439);
      
    // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
    if (upperbound<lowerbound)
    {
    	String errorMsg = "PostTours: UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
    	throw new InvalidPersonPatternException(pattern, errorMsg);
    }

    // Setze die Zeiten in Kategorien um
    if (categories)
    {
    	for (int i=0; i<Configuration.NUMBER_OF_POST_START_TIME_CLASSES; i++)
	    {
	    	if (lowerbound>=Configuration.POST_TOUR_START_TIMECLASSES_LB[i] && lowerbound<=Configuration.POST_TOUR_START_TIMECLASSES_UB[i])
	    	{
	    		lowercat =i;
	    	}
	    	if (upperbound>=Configuration.POST_TOUR_START_TIMECLASSES_LB[i] && upperbound<=Configuration.POST_TOUR_START_TIMECLASSES_UB[i])
	    	{
	    		uppercat =i;
	    	}
	    }
	    
	    // Fehlerbehandlung, falls Kategorien nicht gesetzt werden konnten
	    if (uppercat==-1 || lowercat==-1)
	    {
	    	String errorMsg = "PostTours: Could not identify categories - UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	    	throw new InvalidPersonPatternException(pattern, errorMsg);
	    }
    }
    
    int[] bounds = new int[2];
    if (categories)
    {
    	bounds[0] = lowercat;
    	bounds[1] = uppercat;
    }
    if (!categories)
    {
    	bounds[0] = lowerbound;
    	bounds[1] = upperbound;
    }
    return bounds;
	}

 
  /**
	 * 
	 * @param day
	 * @param tour
	 * @param categories
	 * @return
	 * @throws InvalidPersonPatternException
	 */
	@Deprecated
	@SuppressWarnings({"unused"})
	private int[] calculateBoundsForHomeTime(HDay day, HTour tour, boolean categories) throws InvalidPersonPatternException
	{
		// lowerbound startet mit 1 - upperbound mit 1440 (maximale Heimzeit)
	  int lowerbound = 1;
	  int upperbound = 1440;
	  
	  int lowercat = -1;
	  int uppercat = -1;
	  
	  // Upperbound bestimmt sich aus dem Ende der vorherigen Tour (= schon verbrauchte Zeit) - Dauer der verbleibenden Touren
	  upperbound = 1440 - day.getTour(tour.getIndex()-1).getEndTime();
	  
	  // Gehe alle verbleibenden Touren des Tages durch und ber�cksichtige bereits feststehende Dauern f�r die Festlegung der Grenzen
	  for (int i = tour.getIndex(); i <= day.getHighestTourIndex(); i++)
	  {
	  	upperbound -= day.getTour(i).getTourDuration();
	  }
	  
	  
	  
	  
	  
	  
	  
	  
	  
	        
	  // Fehlerbehandlung, falls UpperBound kleiner ist als LowerBound
	  if (upperbound<lowerbound)
	  {
	  	String errorMsg = "HomeTime Tour " + tour.getIndex() + " : UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	  	throw new InvalidPersonPatternException(pattern, errorMsg);
	  }
	
	  // Zeitklassen falls erforderlich
	  if(categories)
	  {
	    // Setze die Zeiten in Kategorien um
	    for (int i=0; i<Configuration.NUMBER_OF_HOME_DURATION_CLASSES; i++)
	    {
	    	if (lowerbound>=Configuration.HOME_TIME_TIMECLASSES_LB[i] && lowerbound<=Configuration.HOME_TIME_TIMECLASSES_UB[i])
	    	{
	    		lowercat =i;
	    	}
	    	if (upperbound>=Configuration.HOME_TIME_TIMECLASSES_LB[i] && upperbound<=Configuration.HOME_TIME_TIMECLASSES_UB[i])
	    	{
	    		uppercat =i;
	    	}
	    }
	  }
	          
	  // Fehlerbehandlung, falls Kategorien nicht gesetzt werden konnten
	  if(categories)
	  {
	    if (uppercat==-1 || lowercat==-1)
	    {
	    	String errorMsg = "HomeTime Tour " + tour.getIndex() + " : Could not identify categories - UpperBound (" + upperbound + ") < LowerBound (" + lowerbound + ")";
	    	throw new InvalidPersonPatternException(pattern, errorMsg);
	    }
	  }
	    
	  int[] bounds = new int[2];
	  if (categories)
	  {
	  	bounds[0] = lowercat;
	  	bounds[1] = uppercat;
	  }
	  if (!categories)
	  {
	  	bounds[0] = lowerbound;
	  	bounds[1] = upperbound;
	  }
	  return bounds;
	}



	/**
   * 
   * Erstellt Startzeiten f�r jede Aktivit�t
   * 
   */
	@SuppressWarnings("unused")
  private void createStartTimesforActivities()
  {
    for (HDay day : pattern.getDays())
    {
      // ! sorts the list permanently
      HTour.sortTourList(day.getTours());
      for (HTour tour : day.getTours())
      {
        // ! sorts the list permanently
        HActivity.sortActivityList(tour.getActivities());
        for (HActivity act : tour.getActivities())
        {
        	// Bei erster Aktivit�t in Tour wird die Startzeit durch den Beginn der Tour bestimmt
        	if (!act.startTimeisScheduled())
        	{
        		if (act.isActivityFirstinTour())
          	{
          		act.setStartTime(tour.getStartTime() + act.getEstimatedTripTimeBeforeActivity());
          	}
          	// Ansonsten durch das Ende der vorherigen Aktivit�t
          	else
          	{
          		act.setStartTime(act.getPreviousActivityinTour().getEndTime() + act.getEstimatedTripTimeBeforeActivity());
          	}
        	}
        }
      }
    }
  }

	/**
   * 
   * Erstellt Startzeiten f�r jede Aktivit�t einer bestimmten Tour
   * 
   */
  private void createStartTimesforActivities(HTour acttour)
  {
    // ! sorts the list permanently
    HActivity.sortActivityList(acttour.getActivities());
    for (HActivity act : acttour.getActivities())
    {
    	// Bei erster Aktivit�t in Tour wird die Startzeit durch den Beginn der Tour bestimmt
    	if (!act.startTimeisScheduled())
    	{
    		if (act.isActivityFirstinTour())
      	{
      		act.setStartTime(acttour.getStartTime() + act.getEstimatedTripTimeBeforeActivity());
      	}
      	// Ansonsten durch das Ende der vorherigen Aktivit�t
      	else
      	{
      		act.setStartTime(act.getPreviousActivityinTour().getEndTime() + act.getEstimatedTripTimeBeforeActivity());
      	}
    	}
    }
    
    // Pr�fe, ob die Tour einen l�ckenlosen Ablauf hat, das hei�t keine Leerzeit zwischen Akt und Wegen
    if (!tourisFreeofGaps(acttour)) System.err.println("Tour hat L�cken! " + acttour);
  }
  

  /**
   * 
   * Methode erzeugt Home-Aktivit�ten zwischen den Touren
   * 
   * @param allmodeledActivities
   * @throws InvalidPersonPatternException
   */
  private void createHomeActivities(List<HActivity> allmodeledActivities) throws InvalidPersonPatternException, InvalidHouseholdPatternException
  {
  	char homeact = 'H';
  	
  	if(allmodeledActivities.size()!=0)
  	{	
    	// Erstelle zun�chst eine Home-Aktivit�t vor Beginn der ersten Tour
    	int duration1 = allmodeledActivities.get(0).getTripStartTimeBeforeActivityWeekContext();

    	// Es muss immer eine H-Akt zu Beginn m�glich sein
    	assert duration1>0 : "Fehler - keine Home-Aktivit�t zu Beginn m�glich!";
    	if (duration1>0)
    	{
    		pattern.addHomeActivity(new HActivity(pattern.getDay(0), homeact, duration1, 0));
    	}
    	
    	// Durchlaufe alle Aktivit�ten in der Woche - bis auf die letzte
    	for (int i=0; i<allmodeledActivities.size()-1; i++)
    	{
    		HActivity act = allmodeledActivities.get(i);
    		// Wenn Aktivit�t die letzte der Tour ist, erzeuge Heimaktivit�t
    		if (act.isActivityLastinTour())
    		{
    			HTour acttour = act.getTour();
    			HTour nexttour =  allmodeledActivities.get(i+1).getTour();
    			
    			int ende_tour = acttour.getEndTimeWeekContext();
    			int start_next_tour = nexttour.getStartTimeWeekContext();
    			
    			// Bestimme Puffer
    			int duration2 = start_next_tour - ende_tour;
    			if (duration2<=0) 
    			{
    				/*
    				 * Pr�fe, ob die letzte Akt der Tour und die erste Akt der n�chsten Tour �ber gemeinsame Aktivit�ten hinzugef�gt wurden
    				 *  Falls ja, wird der gesamte Haushalt neu modelliert, da �ber Neumodellierung der Person kein Erfolg erzielt werden kann.
    				 */
    				if (acttour.getLastActivityInTour().getCreatorPersonIndex() != person.getPersIndex() && 
    						nexttour.getFirstActivityInTour().getCreatorPersonIndex() != person.getPersIndex())
    				{
    					throw new InvalidHouseholdPatternException(pattern, "keine Zeit f�r Heimaktivit�t zwischen Touren " + acttour + " " + nexttour);
    				}
    				/*
    				 * Ansonsten modelliere nur die Person neu, da durch neue Zufallszahl eine der Konfliktaktivit�ten ggf. zeitliche korrekt modelliert werden kann.
    				 */
    				else
    				{
    					//TODO Aktivieren
    				// throw new InvalidPersonPatternException(pattern, "keine Zeit f�r Heimaktivit�t zwischen Touren " + acttour + " " + nexttour);
    				}
    			}
    			assert duration2>0 : "Fehler - keine Home-Aktivit�t nach Ende der Tour m�glich! - " + start_next_tour + " // " + ende_tour;
    			// Bestimme zugeh�rigen Tag zu der Heimaktivit�t
    			int day = (int) ende_tour/1440;
    			// F�ge Heimaktivit�t in Liste hinzu
    			if (duration2>0)
    			{
    				pattern.addHomeActivity(new HActivity(pattern.getDay(day), homeact, duration2, ende_tour%1440));
    			}
    		}
    	}
    	
    	// Pr�fe, ob nach der letzten Aktivit�t noch Zeit f�r Heim-Aktivit�t ist
    	HActivity lastact = allmodeledActivities.get(allmodeledActivities.size()-1);
    	int ende_lastTour = lastact.getTour().getEndTimeWeekContext();
    	if (ende_lastTour<10080)
    	{
    		// Bestimme Puffer
    		int duration3 = 10080 - ende_lastTour;
    		// Bestimme zugeh�rigen Tag zu der Heimaktivit�t
    		int day = (int) ende_lastTour/1440;
    		// F�ge Heimaktivit�t in Liste hinzu
    		pattern.addHomeActivity(new HActivity(pattern.getDay(day), homeact, duration3, ende_lastTour%1440));
    	}
  	}
  	// In diesem Fall ist die Aktivit�tenliste komplett leer - erzeuge eine Heimaktivit�t f�r die ganze Woche
    else
    {
    	pattern.addHomeActivity(new HActivity(pattern.getDay(0), homeact, 10080, 0));
    }
  }
  
  /**
   * 
   * Methode bestimmt, mit welchen anderen Personen, bisher im Haushalt noch nicht modellierten Personen
   * gemeinsame Aktivit�ten und Wege durchgef�hrt werden und f�gt diese den Listen der Personen hinzu
   * 
   */
	private void generateJointActionsforOtherPersons() 
	{

		for (HActivity tmpactivity : pattern.getAllOutofHomeActivities()) 
		{
			/*
			 * Aktivit�t in die Liste gemeinsamer Aktivit�ten anderer Personen hinzuf�gen, falls
			 * die Aktivit�t gemeinsam ist UND nicht von einer anderen Person urspr�nglich erzeugt wurde (das hei�t keine gemeinsame Aktivit�t 
			 * des Ursprungs einer anderen Person ist)
			 * 
			 */
			if (tmpactivity.getJointStatus()!=4 && tmpactivity.getCreatorPersonIndex()==person.getPersIndex()) 
			{
				
				//TODO Hier Code einf�gen, der bestimmt mit welcher weiteren Person die Aktivit�t durchgef�hrt wird
				/*
				 * Vereinfachung: Zuf�llige Auswahl einer anderen Person aus dem Haushalt
				 */
				
				// Erstelle Map mit allen anderen Personennummern im Haushalt, die noch nicht modelliert wurden und w�hle zuf�llig eine
				Map<Integer,ActitoppPerson> otherunmodeledpersinhh = new HashMap<Integer, ActitoppPerson>();
				// F�ge zun�chst alle Personen des Haushalts hinzu
				otherunmodeledpersinhh.putAll(person.getHousehold().getHouseholdmembers());				
				// Entferne nacheinander alle Personen, die bereits modelliert wurden (= WeekPattern haben) oder die Person selbst sind
				List<Integer> keyValues = new ArrayList<>(otherunmodeledpersinhh.keySet());
				for (Integer key : keyValues) 
				{
					ActitoppPerson tmpperson = otherunmodeledpersinhh.get(key);
					if (tmpperson.getWeekPattern()!=null || tmpperson.getPersIndex()==person.getPersIndex()) 
					{
						otherunmodeledpersinhh.remove(key);
					}
				}
				
				if (otherunmodeledpersinhh.size()>0)
				{
					// Bestimme, mit wievielen Personen die Aktivit�t durchgef�hrt wird
					int anzahlgemeinsamerpers = 1;//(int) randomgenerator.getRandomValueBetween(1, otherunmodeledpersinhh.size(), 1);
					//TODO Wahrscheinlichkeiten f�r die Anzahl an Personen, die an Akt teilnehmen bestimmen!
					for (int i=1 ; i<= anzahlgemeinsamerpers; i++)
					{
						// W�hle eine zuf�llige Nummer der verbleibenden Personen
						List<Integer> keys = new ArrayList<Integer>(otherunmodeledpersinhh.keySet());
						Integer randomkey = keys.get(randomgenerator.getRandomPersonKey(keys.size()));
						
						// Aktivit�t zur Ber�cksichtigung bei anderer Person aufnehmen
						ActitoppPerson otherperson = otherunmodeledpersinhh.get(randomkey);
						otherperson.addJointActivityforConsideration(tmpactivity);
						
						// Diese Person aus der Liste entfernen und ggf. noch andere Personen in Akt mit aufnehmen
						otherunmodeledpersinhh.remove(randomkey);
					}
				}
			}
		}
	}
  

  /**
   * 
   * @param allActivities_inclHome
   */
	private void convertactiToppPurposesTomobiToppPurposeTypes(List<HActivity> allActivities_inclHome) 
	{
		for (HActivity act : allActivities_inclHome)
		{
			switch (act.getType())
			{
				case 'W':
					//DECISION notwendig - 1 oder 2
					executeStep98(act, "98C");
					break;
				case 'E':
					act.setMobiToppActType((byte) 3);
					break;
				case 'S':
					//DECISION notwendig - 11 oder 41 oder 42	
					executeStep98(act, "98A");
					break;
				case 'L':
					//DECISION notwendig - 12 oder 51 oder 52 oder 53 oder 77
					executeStep98(act, "98B");
					break;
				case 'T':
					act.setMobiToppActType((byte) 6);
					break;
				case 'H':
					act.setMobiToppActType((byte) 7);
					break;
				default:
					System.err.println("Ung�ltiger Modellaktivit�tentyp");
			}
		}
	}

	/**
	 * 
	 * Pr�ft, ob WeekPattern �berlappende Aktivit�ten enth�lt
	 * 
	 * @param weekpattern
	 * @return
	 * @throws InvalidPersonPatternException
	 */
	private boolean weekPatternisFreeofOverlaps(HWeekPattern weekpattern) throws InvalidPersonPatternException
	{
		boolean freeofOverlaps=true;
	
		List<HActivity> allActivities = weekpattern.getAllActivities();
		HActivity.sortActivityListInWeekOrder(allActivities);
    
    for (int i = 0; i < allActivities.size()-1; i++)
    {
    	HActivity aktuelleakt = allActivities.get(i);
    	HActivity naechsteakt = allActivities.get(i+1);
    	
      if (aktuelleakt.checkOverlappingtoOtherActivity(naechsteakt)) 
      {
      	freeofOverlaps = false;
      	throw new InvalidPersonPatternException(weekpattern, "activities are overlapping " + aktuelleakt +  " vs " + naechsteakt);
      }
    }
    return freeofOverlaps;
	}
	
	
	
	/**
	 * 
	 * Pr�ft, ob eine Tour keine Zeitl�cken aufweist, das hei�t ob Akt und Wege direkt aneinander gereiht sind
	 * 
	 * @param tour
	 * @return
	 */
	private boolean tourisFreeofGaps(HTour tour)
	{
		boolean gapfree = true;
		
		List<HActivity> tmpaktliste = tour.getActivities();
		HActivity.sortActivityList(tmpaktliste);
		
		for (int i=0 ; i<tmpaktliste.size()-1; i++)
		{
			HActivity aktuelleakt = tmpaktliste.get(i);
			HActivity naechsteakt = tmpaktliste.get(i+1);
			
			if (aktuelleakt.getEndTimeWeekContext() != naechsteakt.getTripStartTimeBeforeActivityWeekContext()) gapfree=false;
		}	
		
		return gapfree;
	}
	
	
	public ModelFileBase getFileBase()
	{
	    return fileBase;
	}
	
	public RNGHelper getRandomGenerator()
	{
	    return randomgenerator;
	}

  public DiscreteTimeDistribution[][] getModifiedDTDs(int type)
  {
      switch (type)
      {
          case (INDICATOR_ACT_DURATIONS):
              return this.modifiedActDurationDTDs;
          case (INDICATOR_TOUR_STARTTIMES):
              return this.modifiedTourStartDTDs;
      }

      return null;

  }

}
