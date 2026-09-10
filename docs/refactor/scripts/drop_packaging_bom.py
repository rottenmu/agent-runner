import pymysql

dst = pymysql.connect(host='localhost', port=3306, user='root',
                      password='Mysql_8Tq4Rz7Lm2Vn9Kp6Yh3!', database='carrepair', charset='utf8mb4')
c = dst.cursor()
c.execute("SHOW TABLES LIKE 'packaging_bom_%'")
tabs = [r[0] for r in c.fetchall()]
c.execute('SET FOREIGN_KEY_CHECKS=0')
for t in tabs:
    c.execute('DROP TABLE IF EXISTS `%s`' % t)
c.execute('SET FOREIGN_KEY_CHECKS=1')
dst.commit()
dst.close()
print('dropped %d packaging_bom tables' % len(tabs))
