package ru.kosterin.colorChanger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**	 Класс ColorChanger предстовляет собой приложение, которое  находит в исходном xml файле элементы <Neutral>,
 *   внутри которых присутствует структура с координатами <Origin>
 *	 и меняет/добавляет элемент <Actor.Color> с заданным цветом в найденные элементы <Neutral>.
 *
 * 	 Имена исходного и целевого файлов, а также заданный цвет должны содержаться в файле colorChanger.properties.  
 * 
 * 	 Костерин Роман, 2018
 */

public class ColorChanger {
	private static final Logger logger = LogManager.getLogger("logger");
	
	public static void main(String[] args) {
		// Инициализация переменных из Properties
		Properties properties = new Properties();
		try {
			FileInputStream fis = new FileInputStream("colorChanger.properties");
			properties.load(fis);
		} catch (IOException e) {
			logger.error("Ошибка при загрузке properties-файла. Требуется файл colorChanger.properties", e);
			return;
		}
		String srcFileName = properties.getProperty("srcFileName");
		String targetFileName = properties.getProperty("targetFileName");
		String colorStr = properties.getProperty("color");
		Integer[] rgbValues;
		try {
			rgbValues = getRgbValues(colorStr);
		} catch (NumberFormatException e) {
			logger.error("Ошибка при парсинге значения цвета из properties-файла.", e);
			return;
		}		
		if(srcFileName == null) {
			srcFileName = "source_file.xml";
			logger.warn("В properties-файле не найден параметр \"srcFileName\", использовано значение по умолчанию: \"" + srcFileName + "\"");
		}
		if(targetFileName == null) {
			targetFileName = "target_file.xml";
			logger.warn("В properties-файле не найден параметр \"targetFileName\", использовано значение по умолчанию: \"" + targetFileName + "\"");
		}
		// Получение DOM из исходного xml файла
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		builderFactory.setNamespaceAware(true);
		DocumentBuilder builder;
		Document doc = null;
		try {
			builder = builderFactory.newDocumentBuilder();
			doc = builder.parse(properties.getProperty("srcFileName"));
		} catch (ParserConfigurationException | SAXException | IOException e) {
			logger.error("Ошибка при парсинге исходного файла.", e);
			return;
		}
           
		
		XPathFactory xpathFactory = XPathFactory.newInstance();
		XPath xpath = xpathFactory.newXPath();
		XPathExpression expressionForNeutral = null;
		XPathExpression expressionForActorColor = null;
		try {
			expressionForNeutral = xpath.compile("//Origin/ancestor::Neutral");
			expressionForActorColor = xpath.compile("//Origin/ancestor::Neutral/Actor.Color");			
		} catch (XPathExpressionException e) {
			logger.error("Ошибка при создании XPathExpression.", e);
			return;
		}
		
		// получение элементов Neutral и удаление существующих Actor.Color
		Set<Element> colorsForDelete = new HashSet<Element>();
		int colorsForDeleteCount = 0;
		int neutralsCount = 0;
		try {
			NodeList colors = (NodeList) expressionForActorColor.evaluate(doc, XPathConstants.NODESET);
			
			for (int i = 0; i < colors.getLength(); i++) {
				colorsForDelete.add((Element) colors.item(i));
			}
			for (Element e : colorsForDelete) {
				e.getParentNode().removeChild(e);
				colorsForDeleteCount++;
			}

			NodeList neutrals = (NodeList) expressionForNeutral.evaluate(doc, XPathConstants.NODESET);
			
			for (int i = 0; i < neutrals.getLength(); i++) {
				Node neutral = neutrals.item(i);
				Element actorColor = doc.createElement("Actor.Color");
				actorColor.setAttribute("B", rgbValues[2].toString());
				actorColor.setAttribute("G", rgbValues[1].toString());
				actorColor.setAttribute("R", rgbValues[0].toString());

				neutral.appendChild(actorColor);
				neutralsCount++;
			}			
			
		} catch (XPathExpressionException e) {
			logger.error("Ошибка при получении элементов Neutral или удалении существующих Actor.Color.", e);
			return;
		}
		
		if(neutralsCount > 0) {
		
	        //Экспорт модифицированного DOM 
	        TransformerFactory tranFactory = TransformerFactory.newInstance();
	        Transformer transformer;
			try {
				transformer = tranFactory.newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				Source src = new DOMSource(doc);
		        Result dest = new StreamResult(new File(targetFileName));
		        transformer.transform(src, dest);
			} catch (TransformerException e) {
				logger.error("Ошибка при сохранении файла " + targetFileName, e);
				return;
			}
			
			logger.info("Изменено элементов Neutral: " + neutralsCount +
						". Из них замена цвета произведена в " + colorsForDeleteCount + " элементах");
		} else {
			logger.info("Элементы Neutral, удовлетворяющие условиям, не найдены в исходном файле. Файл " + targetFileName + " не сформирован");
		}
	}
	
	static Integer[] getRgbValues(String colorString) throws NumberFormatException {
		Integer[] values = new Integer[3];
		boolean colorFormatIsFail = false;
		if(colorString == null)
			colorFormatIsFail = true;
		else {
			if(colorString.length() == 7) { //значение в hex ("#FF0000")
				values[0] = Integer.valueOf(colorString.substring(1,3), 16);
				values[1] = Integer.valueOf( colorString.substring( 3, 5 ), 16 );
				values[2] = Integer.valueOf( colorString.substring( 5, 7 ), 16 );
			} else { //значение в RGB ("RRR;GGG;BBB")
				String[] strValues = colorString.split(";");
				if (strValues.length == 3) {
					int r = Integer.parseInt(strValues[0]);
					int g = Integer.parseInt(strValues[1]);
					int b = Integer.parseInt(strValues[2]);
					if (r < 0 || r > 255 ||
						g < 0 || g > 255 ||
						b < 0 || b > 255
						) {
							colorFormatIsFail = true;
					} else {
							values[0] = r;
							values[1] = g;
							values[2] = b;						
					}
				} else
					colorFormatIsFail = true;
			}
		}
		
		if(colorFormatIsFail)
			throw new NumberFormatException();
		
		return values;		
	}
}
