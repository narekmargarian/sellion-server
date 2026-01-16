package com.sellion.sellionserver.config;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.Role;
import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class DataInitializer {


//
//    @Bean
//    CommandLineRunner initDatabase(ProductRepository productRepo, ClientRepository clientRepo , UserRepository userRepository) {
//        return args -> {
//            // 1. ЗАПОЛНЯЕМ ТОВАРЫ (если база пуста)
//            if (productRepo.count() == 0) {
//                List<Product> products = Arrays.asList(
//                        // Сладкое
//                        new Product(null, "Шарики Манго в какао-глазури ВМ", 930.0, 12, "46450123456701", "Сладкое",12),
//                        new Product(null, "Шарики Манго в белой глазури ВМ", 930.0, 12, "4601234567021", "Сладкое",152),
//                        new Product(null, "Шарики Банано в глазури ВМ", 730.0, 12, "46012345612702", "Сладкое",300),
//                        new Product(null, "Шарики Имбирь сладкий в глазури ВМ", 930.0, 12, "4601234256702", "Сладкое",45),
//
//                        // Чипсы
//                        new Product(null, "Чипсы кокосовые ВМ Оригинальные", 730.0, 12, "4601234856704", "Чипсы",125),
//                        new Product(null, "Чипсы кокосовые ВМ Соленая карамель", 730.0, 12, "4601234456704", "Чипсы",1250),
//                        new Product(null, "Чипсы кокосовые Costa Cocosta", 430.0, 12, "4601234456704", "Чипсы",366),
//                        new Product(null, "Чипсы кокосовые Costa Cocosta Васаби", 430.0, 12, "4601234566705", "Чипсы",247),
//
//                        // Чай
//                        new Product(null, "Чай ВМ Лемонграсс и ананас", 1690.0, 10, "44601234411556706", "Чай",214),
//                        new Product(null, "Чай ВМ зеленый с фруктами", 1690.0, 10, "4604123456706", "Чай",14),
//                        new Product(null, "Чай ВМ черный Мята и апельсин", 1690.0, 10, "1460123456706", "Чай",32),
//                        new Product(null, "Чай ВМ черный Черника и манго", 1990.0, 10, "4601233456707", "Чай",888),
//                        new Product(null, "Чай ВМ черный Шишки и саган-дайля", 1990.0, 10, "4601233456707", "Чай",48),
//                        new Product(null, "Чай ВМ зеленый Жасмин и манго", 1990.0, 10, "4601233456707", "Чай",9633),
//                        new Product(null, "Чай ВМ черный Цветочное манго", 590.0, 12, "4601233456707", "Чай",651),
//                        new Product(null, "Чай ВМ черный Шишки и клюква", 790.0, 12, "4601233456707", "Чай",321),
//                        new Product(null, "Чай ВМ черный Нежная черника", 790.0, 12, "4601233456707", "Чай",258),
//                        new Product(null, "Чай ВМ черный Ассам Цейлон", 1190.0, 14, "4601233456707", "Чай",2114),
//                        new Product(null, "Чай ВМ черный \"Хвойный\"", 790.0, 12, "4601233456707", "Чай",885),
//                        new Product(null, "Чай ВМ черный \"Русский березовый\"", 790.0, 12, "4601233456707", "Чай",123),
//                        new Product(null, "Чай ВМ черный Шишки и малина", 790.0, 12, "4601233456707", "Чай",123),
//
//                        // Сухофрукты
//                        new Product(null, "Сух. Манго сушеное Вкусы мира", 1490.0, 12, "44601234411556706", "Сухофрукты",325),
//                        new Product(null, "Сух. Манго сушеное ВМ Чили", 1490.0, 12, "4604123456706", "Сухофрукты",248),
//                        new Product(null, "Сух. Папайя сушеная Вкусы мира", 1190.0, 12, "1460123456706", "Сухофрукты",741),
//                        new Product(null, "Сух. Манго шарики из сушеного манго", 1190.0, 12, "4601233456707", "Сухофрукты",698),
//                        new Product(null, "Сух. Манго Сушеное LikeDay (250г)", 2490.0, 14, "4601233456707", "Сухофрукты",546),
//                        new Product(null, "Сух. Манго Сушеное LikeDay (100г)", 1190.0, 12, "4601233456707", "Сухофрукты",231),
//                        new Product(null, "Сух.Бананы вяленые Вкусы мира", 1190.0, 12, "4601233456707", "Сухофрукты",258),
//                        new Product(null, "Сух.Джекфрут сушеный Вкусы мира", 1190.0, 12, "4601233456707", "Сухофрукты",369),
//                        new Product(null, "Сух.Ананас сушеный Вкусы мира", 1190.0, 12, "4601233456707", "Сухофрукты",145)
//                );
//                productRepo.saveAll(products);
//            }
//            if (userRepository.count() == 0) {
//                // 1. ДИРЕКТОР (Ты)
//                userRepository.save(new User(null, "boss", "1234", "Твое Имя", Role.ADMIN));
//
//                // 2. ОПЕРАТОРЫ (3 человека)
//                userRepository.save(new User(null, "op1", "1111", "Оператор Анна", Role.OPERATOR));
//                userRepository.save(new User(null, "op2", "1111", "Оператор Мариам", Role.OPERATOR));
//                userRepository.save(new User(null, "op3", "1111", "Оператор Давид", Role.OPERATOR));
//
//                // 3. БУХГАЛТЕРЫ (2 человека)
//                userRepository.save(new User(null, "acc1", "2222", "Бухгалтер Арам", Role.ACCOUNTANT));
//                userRepository.save(new User(null, "acc2", "2222", "Бухгалтер Елена", Role.ACCOUNTANT));
//
//                System.out.println(">>> СОТРУДНИКИ ОФИСА ДОБАВЛЕНЫ <<<");
//            }
//
//            // 2. ЗАПОЛНЯЕМ МАГАЗИНЫ
//            if (clientRepo.count() == 0) {
//                String[] days = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"};
//
//                String[] zovq = {"Շրջանային", "Աէրացիա", "Ամիրյան", "Ավան", "Արշակունյաց", "Բաբաջանյան", "Բագրատունյաց", "Բակունց", "Բեկնազարյան", "Գալշոյան", "Գյուլբենկյան", "Գյուրջյան", "Դավթաշեն", "Դավիթ-Բեկ", "Դրո", "Զվարթնոց26", "Լենինգրադյան", "Խորենացի", "Կոմիտաս", "Հ․ Հակոբյան", "Հանրապետություն", "Մոնումենտ", "Մուրացան", "Նալբանդյան", "Շերամ", "Շիրազ", "Սարյան", "Վերին Պտղնի", "Վիլնյուս", "Փափազյան", "Քաջազնունի", "Հյուսիսային"};
//
//                for (int i = 0; i < zovq.length; i++) {
//                    String day = days[i % 6];
//
//                    // ДОБАВЛЯЕМ ЛОГИКУ ДОЛГА:
//                    // Например, каждому третьему магазину дадим случайный долг для теста
//                    Double testDebt = (i % 3 == 0) ? (15000.0 + (i * 1000)) : 0.0;
//
//                    clientRepo.save(new Client(
//                            null,
//                            "Զովք " + zovq[i],
//                            "Ереван",
//                            "ИП Зովք",
//                            "000000" + i,
//                            "00-00",
//                            day,
//                            testDebt // Передаем долг в конструктор (проверь, что в Entity Client он есть)
//                    ));
//                }
//
//                String[] carrefour = {"YM", "Abovyan", "RM", "hanrapetutyun", "davtashen", "azatutyun", "gyulbekyan", "antarayin", "argishti", "buzand", "paraqar", "masiv", "ajapnyak"};
//                for (int i = 0; i < carrefour.length; i++) {
//                    String day = days[i % 6];
//                    clientRepo.save(new Client(null, "Carrefour " + carrefour[i], "Ереван", "ООО Карфур", "111111", "11-11", day,0.0));
//                }
//            }
//            System.out.println(">>> БАЗА ДАННЫХ УСПЕШНО ЗАПОЛНЕНА РЕАЛЬНЫМИ ДАННЫМИ <<<");
//        };
//    }


}