// ConvertUtils.h

#include <vector>
#include <string>

#include "../../Common/MyString.h"

typedef unsigned char BYTE;

/*
 * Class for converting strings and bytes in various encodings.
 * Just convenience utilities really.
 */
class ConvertUtils {
public:
	static UString encodeBase64(unsigned char const * bytes , unsigned int len);

	static UString cvrtStrToUni(const std::string & str);

	static std::string cvrtUniToStr(const UString & str);

	static std::vector<BYTE> cvrtUniToByteArr(const UString & str);

  static uint64_t bytesToUInt64(std::vector<BYTE> bytes);

  static std::vector<BYTE> uInt64ToBytes(uint64_t uint64);
};